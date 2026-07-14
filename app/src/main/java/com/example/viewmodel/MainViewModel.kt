package com.example.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.webkit.CookieManager
import android.webkit.WebView
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.NovelRepository
import com.example.data.ai.SherpaOnnxTtsEngine
import com.example.data.ai.PiperVoice
import com.example.data.ai.PiperVoiceCatalog
import com.example.ui.theme.AppTheme
import com.example.util.CloudflareException
import com.example.util.NovelCompiler
import com.example.util.TomatoScraper
import com.example.data.scraper.SourceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = NovelRepository(database.bookDao())

    // --- Global Theme & Preferences ---
    private val prefs = application.getSharedPreferences("novel_hoarder_prefs", Context.MODE_PRIVATE)

    var focusModeEnabled by mutableStateOf(false)
    var ttsAutoScrollEnabled by mutableStateOf(true)
    var ttsTotalParagraphs by mutableStateOf(0)

    // Resumable session state
    var hasResumableSession by mutableStateOf(false)
        private set
    var resumeBookId by mutableStateOf("")
        private set
    var resumeChapterId by mutableStateOf("")
        private set
    var resumeParagraph by mutableStateOf(0)
        private set
    var resumeBookName by mutableStateOf("")
        private set
    var resumeChapterTitle by mutableStateOf("")
        private set

    var currentTheme by mutableStateOf(AppTheme.IMMERSIVE_UI)
        private set

    var readerFontSize by mutableStateOf(16)
        private set

    var readerFontFamily by mutableStateOf("serif")
        private set

    var defaultUserAgent by mutableStateOf("")
        private set

    // --- AI Configuration and State ---
    var activeAiProviderId by mutableStateOf("gemini_cloud")
        private set

    var userGeminiApiKey by mutableStateOf("")
        private set

    // --- Auto-download / Reading Queue ---
    var autoDownloadNextEnabled by mutableStateOf(true)

    // --- Reader Settings ---
    var readerTheme by mutableStateOf("auto") // "light", "dark", "auto"
    var readerLineHeight by mutableStateOf(1.4f)
    var readerMargin by mutableStateOf(16) // in dp padding
    var readerLetterSpacing by mutableStateOf(0.0f)
    var readerCustomFontPath by mutableStateOf("")
    var readerCustomFontName by mutableStateOf("")
    var readerJustificationEnabled by mutableStateOf(false)
    var readerHyphenationEnabled by mutableStateOf(false)
    var readerAmbientSyncEnabled by mutableStateOf(false)

    // --- Customizable AI Prompts ---
    var glossaryPrompt by mutableStateOf("")
    var polishPrompt by mutableStateOf("")
    var recapPrompt by mutableStateOf("")

    // --- Scrape Error/Retry Tracking ---
    var failedChaptersList by mutableStateOf<List<MissingChapter>>(emptyList())

    // --- Multi-Select Mode for Library ---
    var isLibraryMultiSelectMode by mutableStateOf(false)
    var selectedLibraryBookIds by mutableStateOf<Set<String>>(emptySet())

    val aiRegistry = com.example.data.ai.AiProviderRegistry(application)
    val modelManager = com.example.data.ai.ModelManager(application)

    init {
        val themeName = prefs.getString("selected_theme", AppTheme.IMMERSIVE_UI.name)
        currentTheme = AppTheme.valueOf(themeName ?: AppTheme.IMMERSIVE_UI.name)
        readerFontSize = prefs.getInt("reader_font_size", 18)
        readerFontFamily = prefs.getString("reader_font_family", "serif") ?: "serif"
        defaultUserAgent = prefs.getString("user_agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36") ?: ""
        activeAiProviderId = prefs.getString("active_ai_provider", "gemini_cloud") ?: "gemini_cloud"
        userGeminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        focusModeEnabled = prefs.getBoolean("focus_mode", false)
        ttsAutoScrollEnabled = prefs.getBoolean("tts_auto_scroll", true)

        autoDownloadNextEnabled = prefs.getBoolean("auto_download_next", true)
        readerTheme = prefs.getString("reader_theme", "auto") ?: "auto"
        readerLineHeight = prefs.getFloat("reader_line_height", 1.4f)
        readerMargin = prefs.getInt("reader_margin", 16)
        readerLetterSpacing = prefs.getFloat("reader_letter_spacing", 0.0f)
        readerCustomFontPath = prefs.getString("reader_custom_font_path", "") ?: ""
        readerCustomFontName = prefs.getString("reader_custom_font_name", "") ?: ""
        readerJustificationEnabled = prefs.getBoolean("reader_justification_enabled", false)
        readerHyphenationEnabled = prefs.getBoolean("reader_hyphenation_enabled", false)
        readerAmbientSyncEnabled = prefs.getBoolean("reader_ambient_sync_enabled", false)

        glossaryPrompt = prefs.getString("glossary_prompt", "Analyze the following novel content and identify character names, locations, and unique terms that are poorly machine-translated or require a consistent translation glossary.") ?: "Analyze the following novel content and identify character names, locations, and unique terms that are poorly machine-translated or require a consistent translation glossary."
        polishPrompt = prefs.getString("polish_prompt", "Rewrite this machine-translated chapter to be in fluent, literary, highly readable English. Preserve the exact original plot, character actions, and meaning. Do not add any commentary or prefix/suffix notes. Only return the polished story text.") ?: "Rewrite this machine-translated chapter to be in fluent, literary, highly readable English. Preserve the exact original plot, character actions, and meaning. Do not add any commentary or prefix/suffix notes. Only return the polished story text."
        recapPrompt = prefs.getString("recap_prompt", "Provide a concise summary ('Previously on...') of the following chapter. Focus on key plot points and character actions in 2-3 sentences. Do not add metadata or conversational padding.") ?: "Provide a concise summary ('Previously on...') of the following chapter. Focus on key plot points and character actions in 2-3 sentences. Do not add metadata or conversational padding."

        registerTtsReceiver()
        loadResumableTtsSession()
        scheduleChapterUpdatesCheck()
    }

    fun updateGeminiApiKey(key: String) {
        userGeminiApiKey = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun updateActiveAiProvider(providerId: String) {
        activeAiProviderId = providerId
        prefs.edit().putString("active_ai_provider", providerId).apply()
    }

    fun updateTheme(theme: AppTheme) {
        currentTheme = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun updateFontSize(size: Int) {
        readerFontSize = size.coerceIn(12, 36)
        prefs.edit().putInt("reader_font_size", readerFontSize).apply()
    }

    fun updateFontFamily(family: String) {
        readerFontFamily = family
        prefs.edit().putString("reader_font_family", family).apply()
    }

    fun updateReaderTheme(theme: String) {
        readerTheme = theme
        prefs.edit().putString("reader_theme", theme).apply()
    }

    fun updateReaderLineHeight(height: Float) {
        readerLineHeight = height
        prefs.edit().putFloat("reader_line_height", height).apply()
    }

    fun updateReaderMargin(margin: Int) {
        readerMargin = margin
        prefs.edit().putInt("reader_margin", margin).apply()
    }

    fun updateReaderLetterSpacing(spacing: Float) {
        readerLetterSpacing = spacing.coerceIn(-0.05f, 0.25f)
        prefs.edit().putFloat("reader_letter_spacing", readerLetterSpacing).apply()
    }

    fun updateCustomFont(path: String, name: String) {
        readerCustomFontPath = path
        readerCustomFontName = name
        prefs.edit()
            .putString("reader_custom_font_path", path)
            .putString("reader_custom_font_name", name)
            .apply()
    }

    fun updateJustificationEnabled(enabled: Boolean) {
        readerJustificationEnabled = enabled
        prefs.edit().putBoolean("reader_justification_enabled", enabled).apply()
    }

    fun updateHyphenationEnabled(enabled: Boolean) {
        readerHyphenationEnabled = enabled
        prefs.edit().putBoolean("reader_hyphenation_enabled", enabled).apply()
    }

    fun updateAmbientSyncEnabled(enabled: Boolean) {
        readerAmbientSyncEnabled = enabled
        prefs.edit().putBoolean("reader_ambient_sync_enabled", enabled).apply()
    }

    fun importCustomFont(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "CustomFont.ttf"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }

                val fontsDir = File(context.filesDir, "fonts")
                if (!fontsDir.exists()) {
                    fontsDir.mkdirs()
                }
                fontsDir.listFiles()?.forEach { it.delete() }

                val destFile = File(fontsDir, displayName)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (destFile.exists()) {
                    withContext(Dispatchers.Main) {
                        updateCustomFont(destFile.absolutePath, displayName)
                        updateFontFamily("custom")
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateAutoDownloadNextEnabled(enabled: Boolean) {
        autoDownloadNextEnabled = enabled
        prefs.edit().putBoolean("auto_download_next", enabled).apply()
    }

    fun updateGlossaryPrompt(prompt: String) {
        glossaryPrompt = prompt
        prefs.edit().putString("glossary_prompt", prompt).apply()
    }

    fun updatePolishPrompt(prompt: String) {
        polishPrompt = prompt
        prefs.edit().putString("polish_prompt", prompt).apply()
    }

    fun updateRecapPrompt(prompt: String) {
        recapPrompt = prompt
        prefs.edit().putString("recap_prompt", prompt).apply()
    }

    // --- Stats state ---
    val totalBooks = repository.allBooks.map { it.size }
    val totalChapters = flow {
        emit(repository.getTotalChapterCount())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Scraper State ---
    var scrapeUrl by mutableStateOf("")
    var scrapeBookName by mutableStateOf("")
    var maxChaptersInput by mutableStateOf("")
    var fromChapterInput by mutableStateOf("")
    var toChapterInput by mutableStateOf("")
    var selectedFormat by mutableStateOf("Both") // EPUB, PDF, Both, Database only
    var aggressiveClean by mutableStateOf(false)

    // Live terminal log output (just like the desktop version!)
    private val _scrapeLogs = MutableStateFlow<List<String>>(emptyList())
    val scrapeLogs = _scrapeLogs.asStateFlow()

    var isScraping by mutableStateOf(false)
        private set
    var isScrapePaused by mutableStateOf(false)
        private set
    var shouldSkipCurrentChapter by mutableStateOf(false)
        private set

    // Missing chapters variables
    var isSearchingMissing by mutableStateOf(false)
        private set
    var missingChaptersToScrape by mutableStateOf<List<MissingChapter>>(emptyList())
        private set
    var missingChaptersSummary by mutableStateOf("")
        private set

    // --- Check for New Chapters State ---
    var checkingNewChaptersBookId by mutableStateOf<String?>(null)
    var isCheckingNewChapters by mutableStateOf(false)
    var showNewChaptersDialog by mutableStateOf(false)
    var newChaptersFoundCount by mutableStateOf(0)
    var checkedBookEntity by mutableStateOf<BookEntity?>(null)
    var newChaptersList by mutableStateOf<List<MissingChapter>>(emptyList())

    // --- TTS Minimize State ---
    var isTtsPlayerBarMinimized by mutableStateOf(false)
    var scrapingStatus by mutableStateOf("● Idle")
        private set
    var currentChapterNum by mutableStateOf(0)
        private set
    var totalChaptersToScrape by mutableStateOf(0)
        private set
    var scrapeProgress by mutableStateOf(0f)
        private set

    fun pauseScraping() {
        if (isScraping && !isScrapePaused) {
            isScrapePaused = true
            addLog("Scraping session paused by user.")
        }
    }

    fun resumeScraping() {
        if (isScraping && isScrapePaused) {
            isScrapePaused = false
            addLog("Scraping session resumed.")
        }
    }

    fun skipCurrentChapter() {
        if (isScraping) {
            shouldSkipCurrentChapter = true
            addLog("Skip requested for current chapter.")
        }
    }

    // CAPTCHA verification variables
    var showCaptchaDialog by mutableStateOf(false)
    var captchaUrl by mutableStateOf("")
    private var captchaContinuation: CancellableContinuation<Unit>? = null

    // Manual Interactive Browser variables
    var showManualBrowser by mutableStateOf(false)
    var manualBrowserUrl by mutableStateOf("https://tomatomtl.com")

    fun launchInteractiveBrowser() {
        val url = scrapeUrl.trim()
        manualBrowserUrl = if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
            url
        } else {
            "https://tomatomtl.com"
        }
        showManualBrowser = true
        addLog("Launching Interactive Browser to solve Cloudflare / Log in: $manualBrowserUrl")
    }

    private var scrapeJob: Job? = null

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _scrapeLogs.update { current ->
            val updated = current + "[$time] $msg"
            if (updated.size > 1000) updated.drop(100) else updated
        }
    }

    fun clearLogs() {
        _scrapeLogs.value = emptyList()
    }

    fun startScraping() {
        if (isScraping) return
        val url = scrapeUrl.trim()
        val bookName = scrapeBookName.trim()

        if (url.isEmpty()) {
            addLog("ERROR: Please enter a Novel or Chapter URL!")
            return
        }

        isScraping = true
        isScrapePaused = false
        shouldSkipCurrentChapter = false
        clearLogs()
        if (bookName.isNotEmpty()) {
            addLog("Initiating Scrape Session for: $bookName")
        } else {
            addLog("Initiating Scrape Session (Novel Name will be auto-scraped from URL)")
        }
        scrapingStatus = "● Starting..."

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                runScraperLoop(url, bookName)
            } catch (e: CancellationException) {
                scrapingStatus = "● Cancelled"
                addLog("Scraping session cancelled by user.")
            } catch (e: Exception) {
                scrapingStatus = "● Error: ${e.message}"
                addLog("CRITICAL ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isScraping = false
                    isScrapePaused = false
                    shouldSkipCurrentChapter = false
                    scrapeProgress = 0f
                }
            }
        }
    }

    fun stopScraping() {
        addLog("Stopping scrape session...")
        scrapingStatus = "● Stopping..."
        isScrapePaused = false
        shouldSkipCurrentChapter = false
        scrapeJob?.cancel()
        captchaContinuation?.cancel()
        isScraping = false
    }

    fun searchMissingChapters() {
        if (isSearchingMissing || isScraping) return
        val url = scrapeUrl.trim()
        if (url.isEmpty()) {
            addLog("ERROR: Please enter a Novel or Chapter URL!")
            return
        }
        isSearchingMissing = true
        missingChaptersToScrape = emptyList()
        missingChaptersSummary = ""
        clearLogs()
        addLog("Starting search for missing chapters...")
        
        viewModelScope.launch(Dispatchers.IO) {
            val scraper = SourceManager.getSourceForUrl(url)
            val bookId = scraper.parseBookId(url) ?: "novel_${System.currentTimeMillis()}"
            val bookUrl = if (url.contains("/book/")) {
                val idx = url.indexOf("/book/")
                val bookPart = url.substring(idx)
                val segments = bookPart.split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2) {
                    "https://tomatomtl.com/book/${segments[1]}"
                } else url
            } else url

            addLog("Initializing WebView to fetch Table of Contents...")
            val webView = withContext(Dispatchers.Main) {
                WebView(getApplication<Application>().applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = defaultUserAgent
                }
            }

            try {
                // Fetch chapter list (TOC)
                var chapterUrls = emptyList<String>()
                var tries = 0
                while (chapterUrls.isEmpty() && tries < 3) {
                    try {
                        chapterUrls = scraper.scrapeChapterList(webView, bookUrl)
                        if (chapterUrls.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                webView.loadUrl(bookUrl)
                            }
                            delay(5000)
                            chapterUrls = scraper.scrapeChapterList(webView, bookUrl)
                        }
                    } catch (e: CloudflareException) {
                        addLog("Cloudflare detected. Attempting to bypass...")
                        handleCaptchaChallenge(bookUrl)
                    } catch (e: Exception) {
                        tries++
                        addLog("TOC fetch retry $tries/3: ${e.message}")
                        delay(2000)
                    }
                }

                if (chapterUrls.isEmpty()) {
                    addLog("ERROR: Could not fetch Table of Contents.")
                    withContext(Dispatchers.Main) {
                        isSearchingMissing = false
                        missingChaptersSummary = "Could not load Table of Contents."
                    }
                    return@launch
                }

                addLog("TOC loaded: ${chapterUrls.size} chapters found.")
                
                // Fetch local chapters
                val localChapters = repository.getChapters(bookId)
                val localUrls = localChapters.map { it.url }.toSet()
                val localNums = localChapters.map { it.chapterNumber }.toSet()

                val missingList = mutableListOf<MissingChapter>()
                for ((index, chapUrl) in chapterUrls.withIndex()) {
                    val chapNum = index + 1
                    if (!localUrls.contains(chapUrl) && !localNums.contains(chapNum)) {
                        missingList.add(MissingChapter(chapUrl, chapNum))
                    }
                }

                withContext(Dispatchers.Main) {
                    missingChaptersToScrape = missingList
                    if (missingList.isEmpty()) {
                        missingChaptersSummary = "All ${chapterUrls.size} chapters are already downloaded!"
                        addLog("All chapters are already downloaded locally.")
                    } else {
                        missingChaptersSummary = "Found ${missingList.size} missing chapters."
                        addLog("Found ${missingList.size} missing chapters out of ${chapterUrls.size} total chapters.")
                    }
                    isSearchingMissing = false
                }
            } catch (e: Exception) {
                addLog("ERROR: ${e.message}")
                withContext(Dispatchers.Main) {
                    isSearchingMissing = false
                    missingChaptersSummary = "Error during search: ${e.message}"
                }
            }
        }
    }

    fun checkForNewChapters(book: BookEntity) {
        if (isCheckingNewChapters || isScraping) return
        isCheckingNewChapters = true
        checkingNewChaptersBookId = book.id
        checkedBookEntity = book
        newChaptersFoundCount = 0
        newChaptersList = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scraper = SourceManager.getSourceForUrl(book.url)
                val bookUrl = if (book.url.contains("/book/")) {
                    val idx = book.url.indexOf("/book/")
                    val bookPart = book.url.substring(idx)
                    val segments = bookPart.split("/").filter { it.isNotEmpty() }
                    if (segments.size >= 2) {
                        "https://tomatomtl.com/book/${segments[1]}"
                    } else book.url
                } else book.url

                val webView = withContext(Dispatchers.Main) {
                    WebView(getApplication<Application>().applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = defaultUserAgent
                    }
                }

                var chapterUrls = emptyList<String>()
                var tries = 0
                while (chapterUrls.isEmpty() && tries < 3) {
                    try {
                        chapterUrls = scraper.scrapeChapterList(webView, bookUrl)
                        if (chapterUrls.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                webView.loadUrl(bookUrl)
                            }
                            delay(5000)
                            chapterUrls = scraper.scrapeChapterList(webView, bookUrl)
                        }
                    } catch (e: Exception) {
                        tries++
                        delay(2000)
                    }
                }

                if (chapterUrls.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isCheckingNewChapters = false
                        checkingNewChaptersBookId = null
                    }
                    return@launch
                }

                val localChapters = repository.getChapters(book.id)
                val localUrls = localChapters.map { it.url }.toSet()
                val localNums = localChapters.map { it.chapterNumber }.toSet()

                val missingList = mutableListOf<MissingChapter>()
                for ((index, chapUrl) in chapterUrls.withIndex()) {
                    val chapNum = index + 1
                    if (!localUrls.contains(chapUrl) && !localNums.contains(chapNum)) {
                        missingList.add(MissingChapter(chapUrl, chapNum))
                    }
                }

                withContext(Dispatchers.Main) {
                    newChaptersList = missingList
                    newChaptersFoundCount = missingList.size
                    showNewChaptersDialog = true
                    isCheckingNewChapters = false
                    checkingNewChaptersBookId = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isCheckingNewChapters = false
                    checkingNewChaptersBookId = null
                }
            }
        }
    }

    fun startScrapingNewChapters() {
        val book = checkedBookEntity ?: return
        if (newChaptersList.isEmpty()) return
        scrapeUrl = book.url
        scrapeBookName = book.title
        missingChaptersToScrape = newChaptersList
        startScrapingMissing()
        showNewChaptersDialog = false
    }

    fun startScrapingMissing() {
        if (isScraping) return
        val url = scrapeUrl.trim()
        val bookName = scrapeBookName.trim()

        if (url.isEmpty()) {
            addLog("ERROR: Please enter a Novel or Chapter URL!")
            return
        }

        if (missingChaptersToScrape.isEmpty()) {
            addLog("ERROR: No missing chapters found to scrape. Run search first!")
            return
        }

        isScraping = true
        isScrapePaused = false
        shouldSkipCurrentChapter = false
        clearLogs()
        addLog("Initiating Scrape Session for ${missingChaptersToScrape.size} Missing Chapters.")
        scrapingStatus = "● Starting..."

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                runScraperLoop(url, bookName, missingChaptersToScrape)
            } catch (e: CancellationException) {
                scrapingStatus = "● Cancelled"
                addLog("Scraping session cancelled by user.")
            } catch (e: Exception) {
                scrapingStatus = "● Error: ${e.message}"
                addLog("CRITICAL ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isScraping = false
                    isScrapePaused = false
                    shouldSkipCurrentChapter = false
                    scrapeProgress = 0f
                }
            }
        }
    }

    fun continueScraping() {
        if (isScraping) return
        val url = scrapeUrl.trim()
        if (url.isEmpty()) {
            addLog("ERROR: Please enter a Novel or Chapter URL!")
            return
        }
        val scraper = SourceManager.getSourceForUrl(url)
        val bookId = scraper.parseBookId(url)
        if (bookId == null) {
            addLog("ERROR: Could not determine Novel ID from URL.")
            return
        }
        
        isScraping = true
        isScrapePaused = false
        shouldSkipCurrentChapter = false
        clearLogs()
        addLog("Finding last downloaded chapter to continue...")
        scrapingStatus = "● Initializing..."

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingChapters = repository.getChapters(bookId)
                val maxChapterNum = existingChapters.maxOfOrNull { it.chapterNumber } ?: 0
                addLog("Last downloaded chapter number: $maxChapterNum")
                
                withContext(Dispatchers.Main) {
                    fromChapterInput = (maxChapterNum + 1).toString()
                    toChapterInput = "" // all remaining
                    addLog("Resuming scrape from Chapter ${maxChapterNum + 1}")
                }
                
                runScraperLoop(url, scrapeBookName.trim())
            } catch (e: CancellationException) {
                scrapingStatus = "● Cancelled"
                addLog("Scraping session cancelled by user.")
            } catch (e: Exception) {
                scrapingStatus = "● Error: ${e.message}"
                addLog("CRITICAL ERROR: ${e.message}")
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isScraping = false
                    isScrapePaused = false
                    shouldSkipCurrentChapter = false
                    scrapeProgress = 0f
                }
            }
        }
    }

    private suspend fun runScraperLoop(
        url: String,
        bookName: String,
        specificChapters: List<MissingChapter>? = null
    ) {
        val scraper = SourceManager.getSourceForUrl(url)
        val bookId = scraper.parseBookId(url) ?: "novel_${System.currentTimeMillis()}"
        val bookUrl = if (url.contains("/book/")) {
            val idx = url.indexOf("/book/")
            val bookPart = url.substring(idx)
            val segments = bookPart.split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                "https://tomatomtl.com/book/${segments[1]}"
            } else url
        } else url

        addLog("Initializing scraping WebView on Main thread...")
        val webView = withContext(Dispatchers.Main) {
            WebView(getApplication<Application>().applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = defaultUserAgent
            }
        }

        try {
            // Setup User-Agent & initial cookies
            val userAgent = defaultUserAgent
            var cookies = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(bookUrl) ?: ""
            }

            // 1. Scrape Book Info / TOC
            addLog("Connecting to Book page to extract meta and cover...")
            var bookEntity: BookEntity? = null
            var retryCount = 0

            while (bookEntity == null && retryCount < 3) {
                try {
                    bookEntity = scraper.scrapeBookInfo(webView, bookUrl)
                } catch (e: CloudflareException) {
                    addLog("Cloudflare detected on Book page. Opening Captcha bypass...")
                    handleCaptchaChallenge(bookUrl)
                    // update cookies after bypass
                    cookies = withContext(Dispatchers.Main) {
                        CookieManager.getInstance().getCookie(bookUrl) ?: ""
                    }
                } catch (e: Exception) {
                    retryCount++
                    addLog("TOC Connection retry $retryCount/3 due to: ${e.message}")
                    delay(2000)
                }
            }

            if (bookEntity == null) {
                addLog("ABORTED: Cannot load book metadata or bypass Cloudflare.")
                scrapingStatus = "● Blocked"
                return
            }

            val finalBook = if (bookName.isNotEmpty()) {
                bookEntity.copy(title = bookName)
            } else {
                bookEntity
            }
            var currentBookState = downloadCoverAndSaveMetadata(finalBook, cookies)
            repository.insertBook(currentBookState)
            addLog("Successfully saved book info: ${currentBookState.title} by ${currentBookState.author}")

            // 2. Fetch Chapter List
            addLog("Extracting Table of Contents...")
            var chapterUrls = emptyList<String>()
            try {
                chapterUrls = scraper.scrapeChapterList(webView, bookUrl)
            } catch (e: Exception) {
                addLog("TOC parsing failed, using direct scraping if possible: ${e.message}")
            }

            if (chapterUrls.isEmpty()) {
                addLog("No chapter list found. If you pasted a chapter URL directly, we will try standard crawling.")
            } else {
                addLog("TOC loaded: ${chapterUrls.size} chapters found.")
            }

            // Determine chapters to process (either supplied missing list or range-based list)
            val chaptersToProcess = specificChapters ?: run {
                val maxCap = maxChaptersInput.toIntOrNull() ?: 0
                val fromCap = fromChapterInput.toIntOrNull() ?: 1
                val toCap = toChapterInput.toIntOrNull() ?: 0

                // Filter the URLs to download
                val startIndex = (fromCap - 1).coerceAtLeast(0)
                var filteredUrls = if (chapterUrls.isNotEmpty() && startIndex < chapterUrls.size) {
                    chapterUrls.subList(startIndex, chapterUrls.size)
                } else {
                    listOf(url) // paste chapter URL fallback
                }

                if (toCap > 0 && toCap >= fromCap && toCap - fromCap + 1 <= filteredUrls.size) {
                    filteredUrls = filteredUrls.subList(0, toCap - fromCap + 1)
                }

                if (maxCap > 0 && maxCap < filteredUrls.size) {
                    filteredUrls = filteredUrls.subList(0, maxCap)
                }

                filteredUrls.mapIndexed { idx, chapUrl ->
                    MissingChapter(chapUrl, idx + fromCap)
                }
            }

            totalChaptersToScrape = chaptersToProcess.size
            addLog("Preparing to scrape $totalChaptersToScrape chapters...")

            var sessionDownloadedCount = 0
            val glossaries = repository.getGlossary(currentBookState.id)

            for ((index, item) in chaptersToProcess.withIndex()) {
                if (!isScraping) break

                val chapterUrl = item.url
                val absoluteChapterNum = item.chapterNumber

                shouldSkipCurrentChapter = false // reset for each chapter

                // Wait if paused
                while (isScrapePaused && isScraping) {
                    scrapingStatus = "● Paused (${index + 1}/$totalChaptersToScrape)"
                    delay(500)
                }

                if (!isScraping) break

                currentChapterNum = index + 1
                scrapeProgress = currentChapterNum.toFloat() / totalChaptersToScrape
                scrapingStatus = "● Downloading ($currentChapterNum/$totalChaptersToScrape)..."

                val chapId = scraper.parseChapterId(chapterUrl) ?: "ch_$absoluteChapterNum"
                val fullChapId = "${currentBookState.id}_$chapId"

                // Check if already downloaded locally
                val existing = repository.getChapter(fullChapId)
                if (existing != null && existing.content.length > 100) {
                    addLog("Chapter $absoluteChapterNum already downloaded. Skipping.")
                    continue
                }

                var downloadSuccess = false
                var tries = 0

                while (!downloadSuccess && tries < 3 && isScraping) {
                    if (shouldSkipCurrentChapter) {
                        break
                    }
                    try {
                        val rawContent = scraper.scrapeChapterContent(webView, chapterUrl) { shouldSkipCurrentChapter }
                        val title = rawContent.first
                        var cleanedBody = TomatoScraper.sanitizeText(rawContent.second, aggressiveClean)

                        // Apply customized glossaries if any exist
                        if (glossaries.isNotEmpty()) {
                            cleanedBody = repository.applyGlossary(cleanedBody, glossaries)
                        }

                        // Create MD5 Hash
                        val md5 = java.security.MessageDigest.getInstance("MD5")
                        val hash = md5.digest(cleanedBody.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                        val chapterEntity = ChapterEntity(
                            id = fullChapId,
                            bookId = currentBookState.id,
                            chapterId = chapId,
                            chapterNumber = absoluteChapterNum,
                            title = title,
                            url = chapterUrl,
                            content = cleanedBody,
                            hash = hash
                        )

                        repository.insertChapter(chapterEntity)
                        addLog("Saved Chapter: $title (${cleanedBody.length} chars)")
                        downloadSuccess = true
                        sessionDownloadedCount++

                        // Save reading progress if not set
                        if (index == 0 && currentBookState.lastReadChapterId == null) {
                            currentBookState = currentBookState.copy(lastReadChapterId = fullChapId)
                            repository.updateBook(currentBookState)
                        }
                    } catch (e: CloudflareException) {
                        addLog("Cloudflare CAPTCHA blocked download on chapter $currentChapterNum. Pausing loop...")
                        handleCaptchaChallenge(chapterUrl)
                        // refresh cookies
                        cookies = withContext(Dispatchers.Main) {
                            CookieManager.getInstance().getCookie(bookUrl) ?: ""
                        }
                    } catch (e: Exception) {
                        tries++
                        if (shouldSkipCurrentChapter) {
                            break
                        }
                        addLog("Error on chapter $currentChapterNum, retry $tries/3: ${e.message}")
                        delay(3000)
                    }
                }

                if (shouldSkipCurrentChapter) {
                    addLog("Skipped Chapter $absoluteChapterNum by user request.")
                    shouldSkipCurrentChapter = false
                    continue
                }

                // Simple random delay to respect scraping etiquette and prevent bans (just like desktop engine!)
                if (isScraping && index < chaptersToProcess.size - 1) {
                    val delayTime = (1000L..2500L).random()
                    delay(delayTime)
                }
            }

            // 3. Post-compile formats (EPUB/PDF) if required
            if (sessionDownloadedCount > 0 || chapterUrls.isNotEmpty()) {
                val allLocalChapters = repository.getChapters(currentBookState.id)
                currentBookState = currentBookState.copy(totalChapters = allLocalChapters.size)
                repository.updateBook(currentBookState)

                val context = getApplication<Application>().applicationContext
                val outputFolder = File(context.filesDir, currentBookState.id)
                if (!outputFolder.exists()) outputFolder.mkdirs()

                if (selectedFormat == "EPUB" || selectedFormat == "Both") {
                    scrapingStatus = "● Packaging EPUB..."
                    addLog("Compiling downloaded chapters into EPUB ebook...")
                    val epubFile = File(outputFolder, "${currentBookState.id}.epub")
                    val ok = NovelCompiler.compileEpub(context, currentBookState, allLocalChapters, epubFile)
                    if (ok) {
                        addLog("EPUB Compilation Successful! Path: ${epubFile.name}")
                    } else {
                        addLog("EPUB compilation failed!")
                    }
                }

                if (selectedFormat == "PDF" || selectedFormat == "Both") {
                    scrapingStatus = "● Packaging PDF..."
                    addLog("Compiling downloaded chapters into PDF ebook...")
                    val pdfFile = File(outputFolder, "${currentBookState.id}.pdf")
                    val ok = NovelCompiler.compilePdf(context, currentBookState, allLocalChapters, pdfFile)
                    if (ok) {
                        addLog("PDF Compilation Successful! Path: ${pdfFile.name}")
                    } else {
                        addLog("PDF compilation failed!")
                    }
                }
            }

            scrapingStatus = "● Finished"
            addLog("Scrape Session completed! Successfully processed all targeted chapters.")
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private suspend fun handleCaptchaChallenge(url: String) {
        withContext(Dispatchers.Main) {
            captchaUrl = url
            showCaptchaDialog = true
            scrapingStatus = "● Captcha Verification Required"
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            captchaContinuation = continuation
            continuation.invokeOnCancellation {
                captchaContinuation = null
            }
        }
    }

    fun resumeAfterCaptcha() {
        showCaptchaDialog = false
        captchaContinuation?.resume(Unit) {
            // Cancellation cleanups
        }
        captchaContinuation = null
    }

    // --- Single Chapter and Novel Rescraping Logic ---
    var rescrapingChapterId by mutableStateOf<String?>(null)
    var isRescrapingBookId by mutableStateOf<String?>(null)
    var rescrapeBookProgress by mutableStateOf(0f)

    fun rescrapeSingleChapter(chapter: ChapterEntity, onComplete: (Boolean, String) -> Unit) {
        if (rescrapingChapterId != null) {
            onComplete(false, "Already rescraping another chapter.")
            return
        }
        rescrapingChapterId = chapter.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scraper = SourceManager.getSourceForUrl(chapter.url)
                val webView = withContext(Dispatchers.Main) {
                    WebView(getApplication<Application>().applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = defaultUserAgent
                    }
                }
                
                try {
                    val rawContent = scraper.scrapeChapterContent(webView, chapter.url) { false }
                    val title = rawContent.first
                    var cleanedBody = TomatoScraper.sanitizeText(rawContent.second, aggressiveClean)
                    
                    // Apply customized glossaries if any exist
                    val glossaries = repository.getGlossary(chapter.bookId)
                    if (glossaries.isNotEmpty()) {
                        cleanedBody = repository.applyGlossary(cleanedBody, glossaries)
                    }
                    
                    // Create MD5 Hash
                    val md5 = java.security.MessageDigest.getInstance("MD5")
                    val hash = md5.digest(cleanedBody.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                    
                    val updatedChapter = chapter.copy(
                        title = title,
                        content = cleanedBody,
                        hash = hash,
                        downloadedAt = System.currentTimeMillis()
                    )
                    
                    // Delete any cached polished/translated or recap data
                    repository.deletePolishedChapter(chapter.id)
                    repository.deleteChapterRecap(chapter.id)
                    
                    // Insert the fresh chapter content
                    repository.insertChapter(updatedChapter)
                    
                    withContext(Dispatchers.Main) {
                        rescrapingChapterId = null
                        onComplete(true, "Successfully rescraped chapter: $title")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        try {
                            webView.destroy()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    rescrapingChapterId = null
                    onComplete(false, "Error: ${e.message}")
                }
            }
        }
    }

    fun rescrapeCorruptedChapters(bookId: String, onComplete: (Boolean, String) -> Unit) {
        if (isRescrapingBookId != null) {
            onComplete(false, "Already rescraping another novel.")
            return
        }
        isRescrapingBookId = bookId
        rescrapeBookProgress = 0f
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chapters = repository.getChapters(bookId)
                val book = repository.getBook(bookId)
                if (book == null) {
                    withContext(Dispatchers.Main) {
                        isRescrapingBookId = null
                        onComplete(false, "Novel not found.")
                    }
                    return@launch
                }
                
                val corrupted = chapters.filter { chapter ->
                    val body = chapter.content
                    val bodyLower = body.lowercase()
                    body.length < 2500 && (
                        bodyLower.contains("login to") || 
                        bodyLower.contains("log in") || 
                        bodyLower.contains("sign in to") || 
                        bodyLower.contains("limit exceeded") || 
                        bodyLower.contains("rate limit") || 
                        bodyLower.contains("too many requests") || 
                        bodyLower.contains("access denied") || 
                        bodyLower.contains("unauthorized") || 
                        bodyLower.contains("forbidden") || 
                        bodyLower.contains("create an account") || 
                        bodyLower.contains("membership") || 
                        bodyLower.contains("please register") ||
                        bodyLower.contains("sign in with") ||
                        bodyLower.contains("google login") ||
                        bodyLower.contains("facebook login")
                    )
                }
                
                if (corrupted.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isRescrapingBookId = null
                        onComplete(true, "No corrupted or invalid chapters detected in this novel.")
                    }
                    return@launch
                }
                
                val scraper = SourceManager.getSourceForUrl(book.url)
                val webView = withContext(Dispatchers.Main) {
                    WebView(getApplication<Application>().applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = defaultUserAgent
                    }
                }
                
                var successCount = 0
                val glossaries = repository.getGlossary(bookId)
                
                try {
                    corrupted.forEachIndexed { index, chapter ->
                        try {
                            val rawContent = scraper.scrapeChapterContent(webView, chapter.url) { false }
                            val title = rawContent.first
                            var cleanedBody = TomatoScraper.sanitizeText(rawContent.second, aggressiveClean)
                            
                            if (glossaries.isNotEmpty()) {
                                cleanedBody = repository.applyGlossary(cleanedBody, glossaries)
                            }
                            
                            val md5 = java.security.MessageDigest.getInstance("MD5")
                            val hash = md5.digest(cleanedBody.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                            
                            val updatedChapter = chapter.copy(
                                title = title,
                                content = cleanedBody,
                                hash = hash,
                                downloadedAt = System.currentTimeMillis()
                            )
                            
                            repository.deletePolishedChapter(chapter.id)
                            repository.deleteChapterRecap(chapter.id)
                            repository.insertChapter(updatedChapter)
                            successCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        withContext(Dispatchers.Main) {
                            rescrapeBookProgress = (index + 1).toFloat() / corrupted.size
                        }
                        delay(1500)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        try {
                            webView.destroy()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    isRescrapingBookId = null
                    onComplete(true, "Completed! Successfully rescraped $successCount out of ${corrupted.size} corrupted chapters.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRescrapingBookId = null
                    onComplete(false, "Error: ${e.message}")
                }
            }
        }
    }

    // --- Book Deletion and Export Helpers ---
    fun deleteBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete local folders if any exist
                val context = getApplication<Application>().applicationContext
                val folder = File(context.filesDir, bookId)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
                repository.deleteBook(bookId)
                addLog("Deleted novel database record and associated storage files: $bookId")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun compileFormat(book: BookEntity, format: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val chapters = repository.getChapters(book.id)
            val outputFolder = File(context.filesDir, book.id)
            if (!outputFolder.exists()) outputFolder.mkdirs()

            if (format == "EPUB") {
                val file = File(outputFolder, "${book.id}.epub")
                val ok = NovelCompiler.compileEpub(context, book, chapters, file)
                withContext(Dispatchers.Main) {
                    onFinished(ok, if (ok) file.absolutePath else "")
                }
            } else if (format == "PDF") {
                val file = File(outputFolder, "${book.id}.pdf")
                val ok = NovelCompiler.compilePdf(context, book, chapters, file)
                withContext(Dispatchers.Main) {
                    onFinished(ok, if (ok) file.absolutePath else "")
                }
            }
        }
    }

    // --- Text To Speech (TTS) System ---
    private var tts: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
    var ttsVoices by mutableStateOf<List<VoiceOption>>(emptyList())
    var selectedVoiceId by mutableStateOf<String>("")

    // TTS Playback state
    var ttsPlayingBook by mutableStateOf<BookEntity?>(null)
    var ttsPlayingChapter by mutableStateOf<ChapterEntity?>(null)
    var ttsIsPlaying by mutableStateOf(false)
    var ttsIsPaused by mutableStateOf(false)

    var ttsPitch by mutableStateOf(1.0f)
    var ttsSpeed by mutableStateOf(1.0f)
    var ttsActiveParagraphIndex by mutableStateOf<Int?>(-1)

    // Premium Piper offline voice properties
    val sherpaOnnxTtsEngine = SherpaOnnxTtsEngine(getApplication())
    var premiumVoiceDownloading by mutableStateOf(false)
        private set
    var premiumVoiceDownloadProgress by mutableStateOf(0)
        private set
    var premiumVoiceDownloadError by mutableStateOf<String?>(null)
        private set

    fun isVoiceDownloaded(voice: PiperVoice): Boolean {
        val oldId = sherpaOnnxTtsEngine.selectedVoiceId
        sherpaOnnxTtsEngine.selectedVoiceId = voice.id
        val res = sherpaOnnxTtsEngine.isModelDownloaded()
        sherpaOnnxTtsEngine.selectedVoiceId = oldId
        return res
    }

    fun downloadPremiumVoice(voice: PiperVoice) {
        if (premiumVoiceDownloading) return
        sherpaOnnxTtsEngine.selectedVoiceId = voice.id
        premiumVoiceDownloading = true
        premiumVoiceDownloadError = null
        viewModelScope.launch(Dispatchers.Main) {
            sherpaOnnxTtsEngine.downloadModel(
                onProgress = { progress ->
                    premiumVoiceDownloadProgress = progress
                },
                onSuccess = {
                    premiumVoiceDownloading = false
                    setTtsVoice(VoiceOption(voice.id, "Piper - ${voice.name}", Locale.US))
                    initTts()
                    addLog("Piper Voice (${voice.name}) downloaded successfully.")
                },
                onFailure = { error ->
                    premiumVoiceDownloadError = error
                    premiumVoiceDownloading = false
                    addLog("Error downloading Piper Voice (${voice.name}): $error")
                }
            )
        }
    }

    fun deletePremiumVoice(voice: PiperVoice) {
        val oldVoiceId = sherpaOnnxTtsEngine.selectedVoiceId
        sherpaOnnxTtsEngine.selectedVoiceId = voice.id
        if (sherpaOnnxTtsEngine.deleteModel()) {
            if (selectedVoiceId == voice.id) {
                val defaultVoice = ttsVoices.find { it.id.startsWith("default_") } ?: ttsVoices.firstOrNull()
                defaultVoice?.let { setTtsVoice(it) }
            }
            initTts()
            addLog("Piper Voice (${voice.name}) model deleted.")
        }
        sherpaOnnxTtsEngine.selectedVoiceId = selectedVoiceId
    }

    fun saveSpeakerId(voiceId: String, speakerId: Int) {
        prefs.edit().putInt("tts_speaker_id_$voiceId", speakerId).apply()
        // If it's the currently playing voice, reload the speaker ID
        if (selectedVoiceId == voiceId && ttsIsPlaying) {
            val book = ttsPlayingBook
            val chapter = ttsPlayingChapter
            if (book != null && chapter != null) {
                speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
            }
        }
    }

    fun getSpeakerId(voiceId: String): Int {
        return prefs.getInt("tts_speaker_id_$voiceId", 0)
    }

    // Sleep Timer state
    var sleepTimerMinutes by mutableStateOf(0) // 0 means Never / Off
    var sleepTimerRemainingSeconds by mutableStateOf<Int?>(null)
    private var sleepTimerJob: Job? = null

    // TTS Broadcast Receiver & Notification state
    private var isReceiverRegistered = false
    private val ttsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.ACTION_PLAY_PAUSE" -> {
                    if (ttsIsPlaying) {
                        pauseTts()
                    } else {
                        resumeTts()
                    }
                }
                "com.example.ACTION_PREV_CHAPTER" -> {
                    playPreviousChapterTts()
                }
                "com.example.ACTION_NEXT_CHAPTER" -> {
                    playNextChapterTts()
                }
                "com.example.ACTION_STOP_TTS" -> {
                    stopTts()
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes
        if (minutes <= 0) {
            sleepTimerRemainingSeconds = null
            return
        }
        sleepTimerRemainingSeconds = minutes * 60
        sleepTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && (sleepTimerRemainingSeconds ?: 0) > 0) {
                delay(1000L)
                withContext(Dispatchers.Main) {
                    sleepTimerRemainingSeconds = (sleepTimerRemainingSeconds ?: 1) - 1
                }
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    addLog("Sleep timer expired. Pausing playback.")
                    pauseTts()
                    sleepTimerMinutes = 0
                    sleepTimerRemainingSeconds = null
                }
            }
        }
    }

    private val channelId = "tts_player_channel"
    private val notificationId = 1001

    fun showTtsNotification() {
        val context = getApplication<Application>().applicationContext
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows TTS playback status and buttons"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Actions intents
        val playPauseIntent = Intent("com.example.ACTION_PLAY_PAUSE").apply {
            `package` = context.packageName
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent("com.example.ACTION_PREV_CHAPTER").apply {
            `package` = context.packageName
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 4, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent("com.example.ACTION_NEXT_CHAPTER").apply {
            `package` = context.packageName
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent("com.example.ACTION_STOP_TTS").apply {
            `package` = context.packageName
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app intent
        val openAppIntent = Intent(context, com.example.MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (ttsIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (ttsIsPlaying) "Pause" else "Play"

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(book.title)
            .setContentText(chapter.title)
            .setOngoing(ttsIsPlaying)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "Prev Chapter", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next Chapter", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun dismissTtsNotification() {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun registerTtsReceiver() {
        if (!isReceiverRegistered) {
            val context = getApplication<Application>().applicationContext
            val filter = IntentFilter().apply {
                addAction("com.example.ACTION_PLAY_PAUSE")
                addAction("com.example.ACTION_PREV_CHAPTER")
                addAction("com.example.ACTION_NEXT_CHAPTER")
                addAction("com.example.ACTION_STOP_TTS")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                ttsReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    fun unregisterTtsReceiver() {
        if (isReceiverRegistered) {
            val context = getApplication<Application>().applicationContext
            try {
                context.unregisterReceiver(ttsReceiver)
            } catch (e: Exception) {
                // ignore
            }
            isReceiverRegistered = false
        }
    }

    fun initTts(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }
        addLog("Initializing TextToSpeech engine...")
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val availableVoices = mutableListOf<VoiceOption>()
                try {
                    val rawVoices = tts?.voices
                    if (rawVoices != null) {
                        val sysLocale = Locale.getDefault()
                        for (v in rawVoices) {
                            if (v.locale.language == "en" || v.locale.language == sysLocale.language) {
                                val region = v.locale.displayCountry.ifEmpty { "Default" }
                                val cleanName = v.name.substringAfterLast(".").substringBefore("#").replace("_", " ").uppercase()
                                val displayName = "${v.locale.displayLanguage} ($region) - Voice $cleanName"
                                availableVoices.add(VoiceOption(v.name, displayName, v.locale))
                            }
                        }
                    }
                } catch (e: Exception) {
                    addLog("Error querying TTS voices: ${e.message}")
                }

                if (availableVoices.isEmpty()) {
                    availableVoices.add(VoiceOption("default_en_us", "English (United States) - Default", Locale.US))
                    availableVoices.add(VoiceOption("default_en_gb", "English (United Kingdom) - Default", Locale.UK))
                    availableVoices.add(VoiceOption("default_system", "System Default", Locale.getDefault()))
                }
                
                var finalVoices = availableVoices.distinctBy { it.id }
                val piperVoiceOptions = PiperVoiceCatalog.ALL_VOICES.map { voice ->
                    VoiceOption(voice.id, "Piper - ${voice.name}", Locale.US)
                }
                finalVoices = piperVoiceOptions + finalVoices
                ttsVoices = finalVoices

                var savedVoice = prefs.getString("tts_selected_voice", "") ?: ""
                if (savedVoice == "premium_piper" || savedVoice.isEmpty()) {
                    savedVoice = PiperVoiceCatalog.AMY_LOW.id
                }
                if (savedVoice.isNotEmpty()) {
                    val matched = ttsVoices.find { it.id == savedVoice }
                    if (matched != null) {
                        setTtsVoice(matched)
                    }
                }
                
                val savedPitch = prefs.getFloat("tts_pitch", 1.0f)
                val savedSpeed = prefs.getFloat("tts_speed", 1.0f)
                ttsPitch = savedPitch
                ttsSpeed = savedSpeed

                addLog("TTS successfully initialized with ${ttsVoices.size} voices.")
                onReady?.invoke()
            } else {
                addLog("ERROR: Failed to initialize TextToSpeech engine!")
            }
        }
    }

    fun playVoicePreview(voiceOption: VoiceOption) {
        val sampleText = "Hello! This is a sample of the ${voiceOption.name.replace("Piper - ", "").replace("System: ", "")} voice."
        val isPiper = voiceOption.id.startsWith("vits-piper-")
        val piperVoice = if (isPiper) com.example.data.ai.PiperVoiceCatalog.getVoiceById(voiceOption.id) else null
        val isDownloaded = piperVoice != null && isVoiceDownloaded(piperVoice)

        if (isPiper && isDownloaded) {
            viewModelScope.launch(Dispatchers.Main) {
                tts?.stop()
                sherpaOnnxTtsEngine.selectedVoiceId = voiceOption.id
                sherpaOnnxTtsEngine.selectedSpeakerId = getSpeakerId(voiceOption.id)
                sherpaOnnxTtsEngine.initOnnx()
                sherpaOnnxTtsEngine.speak(
                    text = sampleText,
                    speed = ttsSpeed,
                    pitch = ttsPitch,
                    onStart = { },
                    onDone = { }
                )
            }
        } else if (!isPiper) {
            initTts {
                viewModelScope.launch(Dispatchers.Main) {
                    tts?.stop()
                    val rawVoices = tts?.voices
                    val actualVoice = rawVoices?.find { it.name == voiceOption.id }
                    if (actualVoice != null) {
                        tts?.setVoice(actualVoice)
                    } else {
                        tts?.setLanguage(voiceOption.locale)
                    }
                    tts?.setPitch(ttsPitch)
                    tts?.setSpeechRate(ttsSpeed)
                    tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "preview_${voiceOption.id}")
                }
            }
        }
    }

    fun setTtsVoice(voiceOption: VoiceOption) {
        selectedVoiceId = voiceOption.id
        prefs.edit().putString("tts_selected_voice", voiceOption.id).apply()
        if (voiceOption.id.startsWith("vits-piper-")) {
            sherpaOnnxTtsEngine.selectedVoiceId = voiceOption.id
            sherpaOnnxTtsEngine.selectedSpeakerId = getSpeakerId(voiceOption.id)
        } else if (voiceOption.id.startsWith("default_")) {
            tts?.setLanguage(voiceOption.locale)
        } else {
            try {
                val rawVoices = tts?.voices
                val actualVoice = rawVoices?.find { it.name == voiceOption.id }
                if (actualVoice != null) {
                    tts?.setVoice(actualVoice)
                } else {
                    tts?.setLanguage(voiceOption.locale)
                }
            } catch (e: Exception) {
                tts?.setLanguage(voiceOption.locale)
            }
        }
        
        // INSTANTANEOUS UPDATE: Restart speaking from current paragraph
        if (ttsIsPlaying) {
            val book = ttsPlayingBook
            val chapter = ttsPlayingChapter
            if (book != null && chapter != null) {
                speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
            }
        }
    }

    fun updateTtsSettings(pitch: Float, speed: Float) {
        ttsPitch = pitch
        ttsSpeed = speed
        prefs.edit()
            .putFloat("tts_pitch", pitch)
            .putFloat("tts_speed", speed)
            .apply()
        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)

        // INSTANTANEOUS UPDATE: Restart speaking from current paragraph
        if (ttsIsPlaying) {
            val book = ttsPlayingBook
            val chapter = ttsPlayingChapter
            if (book != null && chapter != null) {
                speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
            }
        }
    }

    fun updateChaptersReadStatus(chapterIds: List<String>, isRead: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateChaptersReadStatus(chapterIds, isRead)
        }
    }

    fun startTtsForBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val chapters = repository.getChapters(book.id)
            if (chapters.isEmpty()) {
                withContext(Dispatchers.Main) {
                    addLog("No chapters found for book: ${book.title}")
                }
                return@launch
            }
            val activeChapter = if (!book.lastReadChapterId.isNullOrEmpty()) {
                chapters.find { it.id == book.lastReadChapterId } ?: chapters.first()
            } else {
                chapters.first()
            }
            withContext(Dispatchers.Main) {
                speak(activeChapter.content, book, activeChapter)
            }
        }
    }

    fun saveTtsProgress() {
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return
        val para = ttsActiveParagraphIndex ?: -1
        prefs.edit()
            .putString("tts_resume_book_id", book.id)
            .putString("tts_resume_chapter_id", chapter.id)
            .putInt("tts_resume_para", para)
            .putBoolean("tts_was_playing", ttsIsPlaying)
            .apply()
    }

    fun clearTtsProgress() {
        prefs.edit()
            .remove("tts_resume_book_id")
            .remove("tts_resume_chapter_id")
            .remove("tts_resume_para")
            .remove("tts_was_playing")
            .apply()
    }

    fun loadResumableTtsSession() {
        val bookId = prefs.getString("tts_resume_book_id", "") ?: ""
        val chapterId = prefs.getString("tts_resume_chapter_id", "") ?: ""
        val para = prefs.getInt("tts_resume_para", -1)
        if (bookId.isNotEmpty() && chapterId.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val book = repository.getBook(bookId)
                val chapter = repository.getChapter(chapterId)
                if (book != null && chapter != null) {
                    withContext(Dispatchers.Main) {
                        resumeBookId = bookId
                        resumeChapterId = chapterId
                        resumeParagraph = para
                        resumeBookName = book.title
                        resumeChapterTitle = chapter.title
                        hasResumableSession = true
                    }
                }
            }
        } else {
            hasResumableSession = false
        }
    }

    fun resumeLastSession() {
        val bookId = prefs.getString("tts_resume_book_id", "") ?: ""
        val chapterId = prefs.getString("tts_resume_chapter_id", "") ?: ""
        val para = prefs.getInt("tts_resume_para", -1)
        if (bookId.isNotEmpty() && chapterId.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val book = repository.getBook(bookId)
                val chapter = repository.getChapter(chapterId)
                if (book != null && chapter != null) {
                    withContext(Dispatchers.Main) {
                        speak(chapter.content, book, chapter, startFromParagraphIndex = para)
                    }
                }
            }
        }
    }

    fun seekToParagraph(index: Int) {
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return
        val clampedIndex = index.coerceIn(-1, ttsTotalParagraphs - 1)
        speak(chapter.content, book, chapter, startFromParagraphIndex = clampedIndex)
    }

    fun skipParagraph(delta: Int) {
        val current = ttsActiveParagraphIndex ?: -1
        val next = current + delta
        seekToParagraph(next)
    }

    fun toggleFocusMode() {
        focusModeEnabled = !focusModeEnabled
        prefs.edit().putBoolean("focus_mode", focusModeEnabled).apply()
    }

    fun toggleTtsAutoScroll() {
        ttsAutoScrollEnabled = !ttsAutoScrollEnabled
        prefs.edit().putBoolean("tts_auto_scroll", ttsAutoScrollEnabled).apply()
    }

    fun speak(text: String, book: BookEntity, chapter: ChapterEntity, startFromParagraphIndex: Int = -1) {
        // Save chapter progress & mark chapter as read
        viewModelScope.launch(Dispatchers.IO) {
            val updatedBook = book.copy(lastReadChapterId = chapter.id)
            repository.updateBook(updatedBook)
            repository.updateChapterReadStatus(chapter.id, true)
        }

        val isPiper = selectedVoiceId.startsWith("vits-piper-")
        val piperVoice = if (isPiper) PiperVoiceCatalog.getVoiceById(selectedVoiceId) else null
        val isDownloaded = piperVoice != null && isVoiceDownloaded(piperVoice)

        if (isPiper && isDownloaded) {
            viewModelScope.launch(Dispatchers.Main) {
                ttsPlayingBook = book
                ttsPlayingChapter = chapter
                ttsIsPlaying = true
                ttsIsPaused = false

                // Stop any other standard TTS
                tts?.stop()

                val glossary = repository.getGlossary(book.id)
                val cleanText = repository.applyGlossary(text, glossary)
                val rawParagraphs = cleanText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                ttsTotalParagraphs = rawParagraphs.size

                if (startFromParagraphIndex < 0) {
                    ttsActiveParagraphIndex = -1
                } else {
                    ttsActiveParagraphIndex = startFromParagraphIndex
                }

                val textToSpeak = if (startFromParagraphIndex >= 0) {
                    rawParagraphs.drop(startFromParagraphIndex).joinToString("\n")
                } else {
                    chapter.title + "\n" + cleanText
                }

                sherpaOnnxTtsEngine.selectedVoiceId = selectedVoiceId
                sherpaOnnxTtsEngine.selectedSpeakerId = getSpeakerId(selectedVoiceId)
                sherpaOnnxTtsEngine.initOnnx()
                sherpaOnnxTtsEngine.speak(
                    text = textToSpeak,
                    speed = ttsSpeed,
                    pitch = ttsPitch,
                    onStart = { premiumIdx ->
                        viewModelScope.launch(Dispatchers.Main) {
                            ttsActiveParagraphIndex = if (startFromParagraphIndex >= 0) {
                                startFromParagraphIndex + premiumIdx
                            } else {
                                premiumIdx - 1
                            }
                            saveTtsProgress()
                            loadResumableTtsSession()
                        }
                    },
                    onDone = {
                        viewModelScope.launch(Dispatchers.Main) {
                            playNextChapterTts()
                        }
                    }
                )

                showTtsNotification()
            }
            return
        }

        initTts {
            viewModelScope.launch(Dispatchers.Main) {
                ttsPlayingBook = book
                ttsPlayingChapter = chapter
                ttsIsPlaying = true
                ttsIsPaused = false

                tts?.setPitch(ttsPitch)
                tts?.setSpeechRate(ttsSpeed)

                // Retrieve glossary replacements for clean speech
                val glossary = repository.getGlossary(book.id)
                val cleanText = repository.applyGlossary(text, glossary)

                // Filter out html/extra characters and split to avoid 4000 char limits
                val rawParagraphs = cleanText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                ttsTotalParagraphs = rawParagraphs.size
                
                tts?.stop()

                if (startFromParagraphIndex < 0) {
                    // Speak title first
                    tts?.speak(chapter.title, TextToSpeech.QUEUE_ADD, null, "title_${chapter.id}")
                    ttsActiveParagraphIndex = -1
                } else {
                    ttsActiveParagraphIndex = startFromParagraphIndex
                }

                rawParagraphs.forEachIndexed { idx, para ->
                    if (idx >= startFromParagraphIndex) {
                        tts?.speak(para, TextToSpeech.QUEUE_ADD, null, "para_${chapter.id}_$idx")
                    }
                }

                showTtsNotification()

                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != null) {
                            if (utteranceId.startsWith("para_${chapter.id}_")) {
                                val idxStr = utteranceId.substringAfterLast("_")
                                val idx = idxStr.toIntOrNull()
                                if (idx != null) {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        ttsActiveParagraphIndex = idx
                                        saveTtsProgress()
                                        loadResumableTtsSession()
                                    }
                                }
                            } else if (utteranceId.startsWith("title_")) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    ttsActiveParagraphIndex = -1
                                    saveTtsProgress()
                                    loadResumableTtsSession()
                                }
                            }
                        }
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId.startsWith("para_${chapter.id}_${rawParagraphs.size - 1}")) {
                            viewModelScope.launch(Dispatchers.Main) {
                                playNextChapterTts()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun playNextChapterTts() {
        val book = ttsPlayingBook ?: return
        val currentChapter = ttsPlayingChapter ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val chapters = repository.getChapters(book.id)
            val currentIdx = chapters.indexOfFirst { it.id == currentChapter.id }
            if (currentIdx != -1 && currentIdx < chapters.size - 1) {
                val nextChapter = chapters[currentIdx + 1]
                withContext(Dispatchers.Main) {
                    speak(nextChapter.content, book, nextChapter)
                }
            } else {
                withContext(Dispatchers.Main) {
                    stopTts()
                }
            }
        }
    }

    fun playPreviousChapterTts() {
        val book = ttsPlayingBook ?: return
        val currentChapter = ttsPlayingChapter ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val chapters = repository.getChapters(book.id)
            val currentIdx = chapters.indexOfFirst { it.id == currentChapter.id }
            if (currentIdx > 0) {
                val prevChapter = chapters[currentIdx - 1]
                withContext(Dispatchers.Main) {
                    speak(prevChapter.content, book, prevChapter)
                }
            } else {
                withContext(Dispatchers.Main) {
                    speak(currentChapter.content, book, currentChapter, startFromParagraphIndex = -1)
                }
            }
        }
    }

    fun pauseTts() {
        if (ttsIsPlaying) {
            if (selectedVoiceId.startsWith("vits-piper-")) {
                sherpaOnnxTtsEngine.stop()
            } else {
                tts?.stop()
            }
            ttsIsPlaying = false
            ttsIsPaused = true
            showTtsNotification()
            saveTtsProgress()
            loadResumableTtsSession()
        }
    }

    fun resumeTts() {
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return
        speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
    }

    fun stopTts() {
        if (selectedVoiceId.startsWith("vits-piper-")) {
            sherpaOnnxTtsEngine.stop()
        } else {
            tts?.stop()
        }
        ttsPlayingBook = null
        ttsPlayingChapter = null
        ttsIsPlaying = false
        ttsIsPaused = false
        ttsActiveParagraphIndex = -1
        dismissTtsNotification()
        clearTtsProgress()
        hasResumableSession = false
    }

    private fun downloadCoverAndSaveMetadata(book: BookEntity, cookies: String): BookEntity {
        val context = getApplication<Application>().applicationContext
        val outputFolder = File(context.filesDir, book.id)
        if (!outputFolder.exists()) outputFolder.mkdirs()

        // 1. Download Cover Image
        var updatedBook = book
        val coverUrl = book.coverUrl
        if (!coverUrl.isNullOrEmpty()) {
            try {
                addLog("Downloading book cover from $coverUrl...")
                val coverFile = File(outputFolder, "cover.jpg")
                val url = java.net.URL(coverUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", defaultUserAgent)
                if (cookies.isNotEmpty()) {
                    conn.setRequestProperty("Cookie", cookies)
                }
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                
                val responseCode = conn.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        java.io.FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    updatedBook = book.copy(coverLocalPath = coverFile.absolutePath)
                    addLog("Book cover downloaded successfully to: ${coverFile.name}")
                } else {
                    addLog("Failed to download book cover (HTTP $responseCode)")
                }
            } catch (e: Exception) {
                addLog("Error downloading book cover: ${e.message}")
                e.printStackTrace()
            }
        }

        // 2. Save info.json metadata
        try {
            val infoFile = File(outputFolder, "info.json")
            val json = org.json.JSONObject().apply {
                put("id", updatedBook.id)
                put("url", updatedBook.url)
                put("title", updatedBook.title)
                put("author", updatedBook.author)
                put("synopsis", updatedBook.synopsis)
                put("coverUrl", updatedBook.coverUrl ?: "")
                put("coverLocalPath", updatedBook.coverLocalPath ?: "")
                put("totalChapters", updatedBook.totalChapters)
                put("updatedAt", updatedBook.updatedAt)
            }
            infoFile.writeText(json.toString(4))
            addLog("Saved metadata info.json for book: ${updatedBook.title}")
        } catch (e: Exception) {
            addLog("Error saving info.json: ${e.message}")
            e.printStackTrace()
        }

        return updatedBook
    }

    // --- Glossary AI State ---
    var isGeneratingGlossary by mutableStateOf(false)
        private set
    var glossaryStatusMessage by mutableStateOf("")
        private set

    // --- Translation Polish State ---
    private val _polishedChaptersLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val polishedChaptersLoading = _polishedChaptersLoading.asStateFlow()

    // --- Chapter Recap State ---
    private val _chapterRecapLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val chapterRecapLoading = _chapterRecapLoading.asStateFlow()

    // --- AI Novel Discovery State ---
    var discoveryItems by mutableStateOf<List<DiscoveryItem>>(emptyList())
        private set
    var isDiscovering by mutableStateOf(false)
        private set
    var discoveryError by mutableStateOf("")
        private set

    // --- AI Glossary Generator ---
    fun generateGlossaryWithAi(book: BookEntity) {
        viewModelScope.launch {
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
                
                val provider = aiRegistry.getActiveProviderForTask(activeAiProviderId, requiresJson = true)
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
        viewModelScope.launch {
            _polishedChaptersLoading.update { it + (chapter.id to true) }
            try {
                val provider = aiRegistry.getActiveProviderForTask(activeAiProviderId, requiresJson = false)
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

    // --- Chapter Recap ---
    suspend fun getChapterRecap(chapter: ChapterEntity, force: Boolean = false): String? {
        if (!force) {
            val cached = repository.getChapterRecap(chapter.id)
            if (cached != null) return cached.summary
        }

        _chapterRecapLoading.update { it + (chapter.id to true) }
        try {
            val provider = aiRegistry.getActiveProviderForTask(activeAiProviderId, requiresJson = false)
            val prompt = """
                Provide a concise summary ('Previously on...') of the following chapter. Focus on key plot points and character actions in 2-3 sentences. Do not add metadata or conversational padding.
                
                Chapter Title: ${chapter.title}
                
                Chapter Content:
                ${chapter.content}
            """.trimIndent()

            val response = provider.generate(prompt, jsonMode = false)
            if (!response.startsWith("Error:")) {
                val recap = response.trim()
                repository.insertChapterRecap(
                    ChapterRecapEntity(
                        chapterId = chapter.id,
                        bookId = chapter.bookId,
                        summary = recap
                    )
                )
                return recap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _chapterRecapLoading.update { it - chapter.id }
        }
        return null
    }

    // --- Ask About Selection ---
    suspend fun askAboutSelection(selection: String, question: String): String {
        try {
            val provider = aiRegistry.getActiveProviderForTask(activeAiProviderId, requiresJson = false)
            val prompt = """
                You are an expert novel reading assistant. A user has selected the following text from a novel: "$selection".
                They have a question about it: "$question".
                Provide a concise, helpful answer explaining the context, translating terms, or clarifying plot details as requested.
            """.trimIndent()
            return provider.generate(prompt, jsonMode = false)
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }

    // --- AI Novel Discovery ---
    fun discoverNovels(topicsAndGenres: String) {
        viewModelScope.launch {
            isDiscovering = true
            discoveryError = ""
            discoveryItems = emptyList()
            try {
                // Fetch the candidate list from AniList helper (or offline fallback)
                val rawNovels = com.example.data.api.AniListHelper.fetchPopularNovels()
                val candidateCatalog = rawNovels.mapIndexed { idx, item ->
                    "${idx + 1}. Title: ${item.title}\nDescription/Tropes: ${item.description}"
                }.joinToString("\n\n")

                val provider = aiRegistry.getActiveProviderForTask(activeAiProviderId, requiresJson = true)
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

    // --- Bulk Find & Replace (Non-AI Utility) ---
    suspend fun bulkFindAndReplace(
        bookId: String?,
        findText: String,
        replaceText: String,
        scopeAllBooks: Boolean
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (findText.isEmpty()) return@withContext Pair(0, 0)
        
        var chaptersModified = 0
        var totalMatchesReplaced = 0
        
        val targetChapters = if (scopeAllBooks) {
            val books = repository.allBooks.firstOrNull() ?: emptyList()
            books.flatMap { repository.getChapters(it.id) }
        } else {
            if (bookId == null) emptyList() else repository.getChapters(bookId)
        }
        
        for (chapter in targetChapters) {
            if (chapter.content.contains(findText, ignoreCase = true)) {
                val regex = Regex(Regex.escape(findText), RegexOption.IGNORE_CASE)
                val matches = regex.findAll(chapter.content).count()
                if (matches > 0) {
                    val updatedContent = chapter.content.replace(findText, replaceText, ignoreCase = true)
                    repository.updateChapterContent(chapter.id, updatedContent)
                    chaptersModified++
                    totalMatchesReplaced += matches
                }
            }
        }
        
        return@withContext Pair(chaptersModified, totalMatchesReplaced)
    }

    // --- API Key Connection Test ---
    fun validateApiKey(key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
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

    // --- Bulk Operations ---
    fun bulkDeleteBooks(bookIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in bookIds) {
                repository.deleteBook(id)
            }
            withContext(Dispatchers.Main) {
                selectedLibraryBookIds = emptySet()
                isLibraryMultiSelectMode = false
            }
        }
    }

    fun bulkReScrapeBooks(bookIds: Set<String>, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var checked = 0
            var added = 0
            for (id in bookIds) {
                val book = repository.getBook(id) ?: continue
                try {
                    val scraper = SourceManager.getSourceForUrl(book.url)
                    val webView = withContext(Dispatchers.Main) {
                        WebView(getApplication<Application>().applicationContext).apply {
                            settings.javaScriptEnabled = true
                        }
                    }
                    val urls = scraper.scrapeChapterList(webView, book.url)
                    if (urls.isNotEmpty()) {
                        val currentCount = repository.getChapterCount(book.id)
                        val diff = urls.size - currentCount
                        if (diff > 0) {
                            added += diff
                        }
                    }
                    checked++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                selectedLibraryBookIds = emptySet()
                isLibraryMultiSelectMode = false
                onResult("Checked $checked novels. Found $added new chapters to download!")
            }
        }
    }

    // --- Library Backup / Restore ---
    fun backupLibrary(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val books = repository.getAllBooks()
                val allChapters = mutableListOf<ChapterEntity>()
                val allGlossaries = repository.getAllGlossaries()
                val allBookmarks = repository.getAllBookmarks()
                
                for (book in books) {
                    allChapters.addAll(repository.getChapters(book.id))
                }
                
                val backupObj = org.json.JSONObject()
                
                // Books
                val booksArray = org.json.JSONArray()
                for (b in books) {
                    val j = org.json.JSONObject().apply {
                        put("id", b.id)
                        put("title", b.title)
                        put("author", b.author)
                        put("coverUrl", b.coverUrl)
                        put("coverLocalPath", b.coverLocalPath)
                        put("totalChapters", b.totalChapters)
                        put("url", b.url)
                        put("lastReadChapterId", b.lastReadChapterId)
                        put("synopsis", b.synopsis)
                    }
                    booksArray.put(j)
                }
                backupObj.put("books", booksArray)
                
                // Chapters
                val chaptersArray = org.json.JSONArray()
                for (c in allChapters) {
                    val j = org.json.JSONObject().apply {
                        put("id", c.id)
                        put("bookId", c.bookId)
                        put("chapterId", c.chapterId)
                        put("chapterNumber", c.chapterNumber)
                        put("title", c.title)
                        put("url", c.url)
                        put("content", c.content)
                        put("hash", c.hash)
                        put("isRead", c.isRead)
                    }
                    chaptersArray.put(j)
                }
                backupObj.put("chapters", chaptersArray)
                
                // Glossaries
                val glossariesArray = org.json.JSONArray()
                for (g in allGlossaries) {
                    val j = org.json.JSONObject().apply {
                        put("id", g.id)
                        put("bookId", g.bookId)
                        put("originalText", g.originalText)
                        put("replacementText", g.replacementText)
                    }
                    glossariesArray.put(j)
                }
                backupObj.put("glossaries", glossariesArray)
                
                // Bookmarks
                val bookmarksArray = org.json.JSONArray()
                for (b in allBookmarks) {
                    val j = org.json.JSONObject().apply {
                        put("id", b.id)
                        put("bookId", b.bookId)
                        put("chapterId", b.chapterId)
                        put("paragraphIndex", b.paragraphIndex)
                        put("text", b.text)
                        put("note", b.note)
                        put("timestamp", b.timestamp)
                    }
                    bookmarksArray.put(j)
                }
                backupObj.put("bookmarks", bookmarksArray)
                
                val backupFile = java.io.File(context.cacheDir, "novel_hoarder_library_backup.json")
                backupFile.writeText(backupObj.toString())
                
                withContext(Dispatchers.Main) {
                    onResult(true, backupFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Failed to generate backup JSON")
                }
            }
        }
    }

    fun restoreLibrary(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Could not open input stream")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val backupObj = org.json.JSONObject(jsonString)
                
                // Books
                val booksArray = backupObj.optJSONArray("books")
                if (booksArray != null) {
                    val list = mutableListOf<BookEntity>()
                    for (i in 0 until booksArray.length()) {
                        val j = booksArray.getJSONObject(i)
                        list.add(
                            BookEntity(
                                id = j.getString("id"),
                                title = j.getString("title"),
                                author = j.getString("author"),
                                coverUrl = j.getString("coverUrl"),
                                coverLocalPath = j.optString("coverLocalPath", null),
                                totalChapters = j.getInt("totalChapters"),
                                url = j.getString("url"),
                                lastReadChapterId = j.optString("lastReadChapterId", null),
                                synopsis = j.optString("synopsis", "")
                            )
                        )
                    }
                    repository.insertBooks(list)
                }
                
                // Chapters
                val chaptersArray = backupObj.optJSONArray("chapters")
                if (chaptersArray != null) {
                    val list = mutableListOf<ChapterEntity>()
                    for (i in 0 until chaptersArray.length()) {
                        val j = chaptersArray.getJSONObject(i)
                        list.add(
                            ChapterEntity(
                                id = j.getString("id"),
                                bookId = j.getString("bookId"),
                                chapterId = j.getString("chapterId"),
                                chapterNumber = j.getInt("chapterNumber"),
                                title = j.getString("title"),
                                url = j.getString("url"),
                                content = j.getString("content"),
                                hash = j.optString("hash", ""),
                                isRead = j.optBoolean("isRead", false)
                            )
                        )
                    }
                    repository.insertChapters(list)
                }
                
                // Glossaries
                val glossariesArray = backupObj.optJSONArray("glossaries")
                if (glossariesArray != null) {
                    val list = mutableListOf<GlossaryEntity>()
                    for (i in 0 until glossariesArray.length()) {
                        val j = glossariesArray.getJSONObject(i)
                        list.add(
                            GlossaryEntity(
                                id = j.getInt("id"),
                                bookId = j.getString("bookId"),
                                originalText = j.getString("originalText"),
                                replacementText = j.getString("replacementText")
                            )
                        )
                    }
                    repository.insertGlossaries(list)
                }
                
                // Bookmarks
                val bookmarksArray = backupObj.optJSONArray("bookmarks")
                if (bookmarksArray != null) {
                    val list = mutableListOf<BookmarkEntity>()
                    for (i in 0 until bookmarksArray.length()) {
                        val j = bookmarksArray.getJSONObject(i)
                        list.add(
                            BookmarkEntity(
                                id = j.getString("id"),
                                bookId = j.getString("bookId"),
                                chapterId = j.getString("chapterId"),
                                paragraphIndex = j.getInt("paragraphIndex"),
                                text = j.getString("text"),
                                note = j.optString("note", ""),
                                timestamp = j.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                    repository.insertBookmarks(list)
                }
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Library restored successfully!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to restore: ${e.message}")
                }
            }
        }
    }

    // --- Import Local File ---
    fun importLocalFile(context: Context, uri: Uri, isEpub: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (isEpub) {
                com.example.util.EpubImporter.importEpub(context, uri)
            } else {
                com.example.util.EpubImporter.importTxt(context, uri)
            }
            
            if (result != null) {
                repository.insertBook(result.first)
                repository.insertChapters(result.second)
                withContext(Dispatchers.Main) {
                    onResult(true, "Imported \"${result.first.title}\" with ${result.second.size} chapters!")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to parse local book file. Ensure the format is valid.")
                }
            }
        }
    }

    // --- Background Chapter Updates Checker ---
    fun scheduleChapterUpdatesCheck() {
        val context = getApplication<Application>().applicationContext
        try {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.background.ChapterUpdateWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            ).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "chapter_updates_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun triggerAutoDownloadNextChapters(book: BookEntity, currentChapter: ChapterEntity) {
        if (!autoDownloadNextEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (book.url.startsWith("local://")) return@launch
                val scraper = SourceManager.getSourceForUrl(book.url)
                val webView = withContext(Dispatchers.Main) {
                    WebView(getApplication<Application>().applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
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
                        var cleanedBody = TomatoScraper.sanitizeText(rawContent.second, aggressiveClean)
                        
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

    fun getSavedParagraphIndex(bookId: String, chapterId: String): Int {
        return prefs.getInt("progress_para_${bookId}_${chapterId}", 0)
    }

    fun saveReadingProgress(bookId: String, chapterId: String, paragraphIndex: Int) {
        prefs.edit()
            .putInt("progress_para_${bookId}_${chapterId}", paragraphIndex)
            .putString("progress_chapter_${bookId}", chapterId)
            .apply()
    }

    fun autoSaveProgressAndBookmark(
        bookId: String,
        chapterId: String,
        paragraphIndex: Int,
        paragraphText: String
    ) {
        saveReadingProgress(bookId, chapterId, paragraphIndex)

        if (paragraphText.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val bookmarkId = "${bookId}_${chapterId}_${paragraphIndex}"
                val bookmark = BookmarkEntity(
                    id = bookmarkId,
                    bookId = bookId,
                    chapterId = chapterId,
                    paragraphIndex = paragraphIndex,
                    text = paragraphText,
                    note = "Auto-saved Progress",
                    timestamp = System.currentTimeMillis()
                )
                repository.insertBookmark(bookmark)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sherpaOnnxTtsEngine.shutdown()
        tts?.stop()
        tts?.shutdown()
        unregisterTtsReceiver()
        dismissTtsNotification()
        sleepTimerJob?.cancel()
    }
}

data class VoiceOption(
    val id: String,
    val name: String,
    val locale: java.util.Locale
)

data class DiscoveryItem(
    val title: String,
    val description: String,
    val searchUrl: String
)

data class MissingChapter(
    val url: String,
    val chapterNumber: Int
)

