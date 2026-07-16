package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.ai.AiProviderRegistry
import com.example.data.ai.GeminiCloudProvider
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.GlossaryEntity
import com.example.data.local.PolishedChapterEntity
import com.example.data.repository.NovelRepository
import com.example.data.scraper.SourceManager
import com.example.util.TomatoScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiFeaturesManager(
    private val repository: NovelRepository,
    private val aiRegistry: AiProviderRegistry,
    private val settings: SettingsManager
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var aggressiveCleanProvider: () -> Boolean = { false }

    // --- Glossary AI State ---
    var isGeneratingGlossary by mutableStateOf(false)
        internal set
    var glossaryStatusMessage by mutableStateOf("")
        internal set

    // --- Translation Polish State ---
    private val _polishedChaptersLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val polishedChaptersLoading = _polishedChaptersLoading.asStateFlow()

    // --- AI Novel Discovery State ---
    var discoveryItems by mutableStateOf<List<DiscoveryItem>>(emptyList())
        internal set
    var isDiscovering by mutableStateOf(false)
        internal set
    var discoveryError by mutableStateOf("")
        internal set

    // --- AI Glossary Generator ---
    fun generateGlossaryWithAi(book: BookEntity) {
        coroutineScope.launch {
            isGeneratingGlossary = true
            glossaryStatusMessage = "Analyzing book content..."
            try {
                val chapters = repository.getChapters(book.id)
                if (chapters.isEmpty()) {
                    glossaryStatusMessage = "No chapters found for this book."
                    isGeneratingGlossary = false
                    return@launch
                }
                
                // Take first ~12k characters
                val sampleText = chapters.take(5).joinToString("\n\n") { it.content }.take(12000)
                glossaryStatusMessage = "Consulting AI provider..."
                
                val provider = aiRegistry.getActiveProviderForTask(settings.activeAiProviderId, requiresJson = true)
                val prompt = """
                    Analyze the following novel content and identify character names, locations, and unique terms that are poorly machine-translated or require a consistent translation glossary. 
                    Return a JSON array containing objects with 'original' (the text in the content, usually a pinyin or direct translation name) and 'replacement' (a natural, polished English name/translation).
                    Only return a JSON array of objects: [{"original": "...", "replacement": "..."}]. Do not return markdown, do not write '```json'. Just raw JSON.
                    
                    Novel Content:
                    $sampleText
                """.trimIndent()
                
                val response = provider.generate(prompt, jsonMode = true)
                if (response.startsWith("Error:")) {
                    glossaryStatusMessage = response
                    isGeneratingGlossary = false
                    return@launch
                }
                
                // Clean markdown codeblocks if model didn't obey
                val cleanResponse = response.substringAfter("```json").substringAfter("```").substringBeforeLast("```").trim()
                
                glossaryStatusMessage = "Parsing terms and deduping..."
                val jsonArray = org.json.JSONArray(cleanResponse)
                val existing = repository.getGlossary(book.id)
                var count = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val original = obj.optString("original", "").trim()
                    val replacement = obj.optString("replacement", "").trim()
                    
                    if (original.isNotEmpty() && replacement.isNotEmpty()) {
                        val alreadyExists = existing.any { it.originalText.equals(original, ignoreCase = true) }
                        if (!alreadyExists) {
                            repository.insertGlossary(
                                GlossaryEntity(
                                    bookId = book.id,
                                    originalText = original,
                                    replacementText = replacement
                                )
                            )
                            count++
                        }
                    }
                }
                glossaryStatusMessage = "Success! Added $count new terms to your glossary."
            } catch (e: Exception) {
                glossaryStatusMessage = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isGeneratingGlossary = false
            }
        }
    }

    // --- Translation Polish ---
    fun polishChapter(chapter: ChapterEntity) {
        coroutineScope.launch {
            _polishedChaptersLoading.update { it + (chapter.id to true) }
            try {
                val provider = aiRegistry.getActiveProviderForTask(settings.activeAiProviderId, requiresJson = false)
                val prompt = """
                    Rewrite this machine-translated chapter to be in fluent, literary, highly readable English. Preserve the exact original plot, character actions, and meaning. Do not add any commentary or prefix/suffix notes. Only return the polished story text.
                    
                    Chapter Title: ${chapter.title}
                    
                    Chapter Content:
                    ${chapter.content}
                """.trimIndent()
                
                val response = provider.generate(prompt, jsonMode = false)
                if (!response.startsWith("Error:")) {
                    repository.insertPolishedChapter(
                        PolishedChapterEntity(
                            chapterId = chapter.id,
                            bookId = chapter.bookId,
                            content = response
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _polishedChaptersLoading.update { it - chapter.id }
            }
        }
    }

    fun getPolishedChapterFlow(chapterId: String): Flow<PolishedChapterEntity?> {
        return repository.getPolishedChapterFlow(chapterId)
    }

    // --- AI Novel Discovery ---
    fun discoverNovels(topicsAndGenres: String) {
        coroutineScope.launch {
            isDiscovering = true
            discoveryError = ""
            discoveryItems = emptyList()
            try {
                // Fetch the candidate list from AniList helper (or offline fallback)
                val rawNovels = com.example.data.api.AniListHelper.fetchPopularNovels()
                val candidateCatalog = rawNovels.mapIndexed { idx, item ->
                    "${idx + 1}. Title: ${item.title}\nDescription/Tropes: ${item.description}"
                }.joinToString("\n\n")

                val provider = aiRegistry.getActiveProviderForTask(settings.activeAiProviderId, requiresJson = true)
                val prompt = """
                    You are a professional light novel and web novel discovery assistant. 
                    Based on the user's preferred topics, genres, and tropes: "$topicsAndGenres", filter and rank the candidate list of web novels below to suggest 3 to 5 popular titles that match their taste.
                    
                    Candidate Web Novel Catalog:
                    $candidateCatalog
                    
                    For each matched book:
                    1. Keep the exact 'title' as given in the list.
                    2. Write a customized, engaging 'description' of 1-2 sentences explaining precisely why it matches their preferences, citing specific tropes (like cultivation, transmigration, steampunk, OP mc, no romance, etc.).
                    
                    Return a JSON array of objects, where each object has 'title' and 'description'.
                    Return ONLY the raw JSON array of objects: [{"title": "...", "description": "..."}]. Do not return markdown. Do not write '```json'.
                """.trimIndent()
                
                val response = provider.generate(prompt, jsonMode = true)
                if (response.startsWith("Error:")) {
                    discoveryError = response
                    isDiscovering = false
                    return@launch
                }
                
                val cleanResponse = response.trim()
                val items = mutableListOf<DiscoveryItem>()
                var jsonArray: org.json.JSONArray? = null
                
                try {
                    var rawJson = cleanResponse
                    if (rawJson.contains("```")) {
                        rawJson = rawJson.substringAfter("```json").substringAfter("```").substringBeforeLast("```").trim()
                    }
                    
                    try {
                        jsonArray = org.json.JSONArray(rawJson)
                    } catch (e: Exception) {
                        // Attempt to extract array brackets if there is leading text
                        val firstBracket = rawJson.indexOf('[')
                        val lastBracket = rawJson.lastIndexOf(']')
                        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
                            val candidate = rawJson.substring(firstBracket, lastBracket + 1)
                            jsonArray = org.json.JSONArray(candidate)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (jsonArray != null) {
                    try {
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val title = obj.optString("title", "").trim()
                            val description = obj.optString("description", "").trim()
                            if (title.isNotEmpty()) {
                                val searchUrl = "https://tomatomtl.com/#/search?search=${java.net.URLEncoder.encode(title, "UTF-8")}"
                                items.add(DiscoveryItem(title, description, searchUrl))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fallback 1: Parse plain-text lists or bullet points
                if (items.isEmpty()) {
                    try {
                        val lines = cleanResponse.lines()
                        var currentTitle = ""
                        var currentDesc = ""
                        for (line in lines) {
                            val trimmedLine = line.trim()
                            if (trimmedLine.contains("Title:", ignoreCase = true)) {
                                if (currentTitle.isNotEmpty()) {
                                    items.add(
                                        DiscoveryItem(
                                            currentTitle,
                                            if (currentDesc.isNotEmpty()) currentDesc else "Matched web novel recommendation.",
                                            "https://tomatomtl.com/#/search?search=${java.net.URLEncoder.encode(currentTitle, "UTF-8")}"
                                        )
                                    )
                                    currentDesc = ""
                                }
                                currentTitle = trimmedLine.substringAfter("Title:", "").trim().removeSurrounding("\"").removeSurrounding("'")
                            } else if (trimmedLine.contains("Description:", ignoreCase = true)) {
                                currentDesc = trimmedLine.substringAfter("Description:", "").trim().removeSurrounding("\"").removeSurrounding("'")
                            } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || (trimmedLine.firstOrNull()?.isDigit() == true && trimmedLine.contains(". "))) {
                                val content = trimmedLine.substringAfter("- ").substringAfter("* ").substringAfter(". ").trim()
                                if (content.isNotEmpty() && !content.contains("JSON") && !content.contains("array")) {
                                    if (currentTitle.isNotEmpty() && currentDesc.isNotEmpty()) {
                                        items.add(
                                            DiscoveryItem(
                                                currentTitle,
                                                currentDesc,
                                                "https://tomatomtl.com/#/search?search=${java.net.URLEncoder.encode(currentTitle, "UTF-8")}"
                                            )
                                        )
                                        currentTitle = ""
                                        currentDesc = ""
                                    }
                                    if (content.contains(":")) {
                                        currentTitle = content.substringBefore(":").trim().removeSurrounding("\"").removeSurrounding("'")
                                        currentDesc = content.substringAfter(":").trim()
                                    } else if (content.contains("-")) {
                                        currentTitle = content.substringBefore("-").trim().removeSurrounding("\"").removeSurrounding("'")
                                        currentDesc = content.substringAfter("-").trim()
                                    } else {
                                        currentTitle = content
                                    }
                                }
                            }
                        }
                        if (currentTitle.isNotEmpty()) {
                            items.add(
                                DiscoveryItem(
                                    currentTitle,
                                    if (currentDesc.isNotEmpty()) currentDesc else "Matched web novel recommendation.",
                                    "https://tomatomtl.com/#/search?search=${java.net.URLEncoder.encode(currentTitle, "UTF-8")}"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fallback 2: Offline smart-matching using user keyword filtering against our light novel catalog
                if (items.isEmpty()) {
                    val keywords = topicsAndGenres.lowercase().split(Regex("[\\s,\\.]+")).filter { it.length > 2 }
                    val candidates = rawNovels.ifEmpty { com.example.data.api.AniListHelper.fetchPopularNovels() }
                    val matched = candidates.filter { candidate ->
                        keywords.any { keyword ->
                            candidate.title.lowercase().contains(keyword) || 
                            candidate.description.lowercase().contains(keyword)
                        }
                    }.take(4)
                    
                    val fallbackList = if (matched.size >= 2) {
                        matched
                    } else {
                        candidates.shuffled().take(3)
                    }
                    
                    fallbackList.forEach { candidate ->
                        items.add(
                            DiscoveryItem(
                                candidate.title,
                                "Locally matched recommendation: ${candidate.description}",
                                "https://tomatomtl.com/#/search?search=${java.net.URLEncoder.encode(candidate.title, "UTF-8")}"
                            )
                        )
                    }
                }

                discoveryItems = items
                if (items.isEmpty()) {
                    discoveryError = "No suggestions found in AI response."
                }
            } catch (e: Exception) {
                discoveryError = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isDiscovering = false
            }
        }
    }

    // --- API Key Connection Test ---
    fun validateApiKey(key: String, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch {
            try {
                val tempProvider = com.example.data.ai.GeminiCloudProvider { key }
                val response = tempProvider.generate("Say 'OK'.", jsonMode = false)
                if (response.startsWith("Error:")) {
                    onResult(false, response)
                } else {
                    onResult(true, "API Connection Successful!")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Connection validation failed.")
            }
        }
    }

    // --- Auto-download Next Chapters ---
    fun triggerAutoDownloadNextChapters(book: BookEntity, currentChapter: ChapterEntity) {
        if (!settings.autoDownloadNextEnabled) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (book.url.startsWith("local://")) return@launch
                val scraper = SourceManager.getSourceForUrl(book.url)
                val webView = withContext(Dispatchers.Main) {
                    WebView(settings.application.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                    }
                }
                
                val chapterUrls = scraper.scrapeChapterList(webView, book.url)
                if (chapterUrls.isEmpty()) return@launch
                
                val currentIdx = chapterUrls.indexOfFirst { it == currentChapter.url }
                if (currentIdx == -1) return@launch
                
                val nextUrls = chapterUrls.drop(currentIdx + 1).take(3)
                val glossaries = repository.getGlossary(book.id)
                
                for ((offset, nextUrl) in nextUrls.withIndex()) {
                    val absoluteChapterNum = currentChapter.chapterNumber + 1 + offset
                    val chapId = scraper.parseChapterId(nextUrl) ?: "ch_$absoluteChapterNum"
                    val fullChapId = "${book.id}_$chapId"
                    
                    val existing = repository.getChapter(fullChapId)
                    if (existing != null && existing.content.length > 100) {
                        continue
                    }
                    
                    try {
                        val rawContent = scraper.scrapeChapterContent(webView, nextUrl) { false }
                        val title = rawContent.first
                        var cleanedBody = TomatoScraper.sanitizeText(rawContent.second, aggressiveCleanProvider())
                        
                        if (glossaries.isNotEmpty()) {
                            cleanedBody = repository.applyGlossary(cleanedBody, glossaries)
                        }
                        
                        val md5 = java.security.MessageDigest.getInstance("MD5")
                        val hash = md5.digest(cleanedBody.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                        
                        val chapterEntity = ChapterEntity(
                            id = fullChapId,
                            bookId = book.id,
                            chapterId = chapId,
                            chapterNumber = absoluteChapterNum,
                            title = title,
                            url = nextUrl,
                            content = cleanedBody,
                            hash = hash
                        )
                        repository.insertChapter(chapterEntity)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
