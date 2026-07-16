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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.media.app.NotificationCompat.MediaStyle
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

    var focusModeEnabled: Boolean
        get() = tts.focusModeEnabled
        set(value) { tts.focusModeEnabled = value }

    var ttsAutoScrollEnabled: Boolean
        get() = tts.ttsAutoScrollEnabled
        set(value) { tts.ttsAutoScrollEnabled = value }

    var ttsTotalParagraphs: Int
        get() = tts.ttsTotalParagraphs
        set(value) { tts.ttsTotalParagraphs = value }

    // Resumable session state
    var hasResumableSession: Boolean
        get() = progress.hasResumableSession
        set(value) { progress.hasResumableSession = value }

    var resumeBookId: String
        get() = progress.resumeBookId
        set(value) { progress.resumeBookId = value }

    var resumeChapterId: String
        get() = progress.resumeChapterId
        set(value) { progress.resumeChapterId = value }

    var resumeParagraph: Int
        get() = progress.resumeParagraph
        set(value) { progress.resumeParagraph = value }

    var resumeBookName: String
        get() = progress.resumeBookName
        set(value) { progress.resumeBookName = value }

    var resumeChapterTitle: String
        get() = progress.resumeChapterTitle
        set(value) { progress.resumeChapterTitle = value }

    val settings = SettingsManager(application)

    val currentTheme: AppTheme get() = settings.currentTheme
    val readerFontSize: Int get() = settings.readerFontSize
    val readerFontFamily: String get() = settings.readerFontFamily
    val defaultUserAgent: String get() = settings.defaultUserAgent

    // --- AI Configuration and State ---
    val activeAiProviderId: String get() = settings.activeAiProviderId
    val userGeminiApiKey: String get() = settings.userGeminiApiKey

    // --- Auto-download / Reading Queue ---
    val autoDownloadNextEnabled: Boolean get() = settings.autoDownloadNextEnabled

    // --- Reader Settings ---
    val readerTheme: String get() = settings.readerTheme
    val readerLineHeight: Float get() = settings.readerLineHeight
    val readerMargin: Int get() = settings.readerMargin
    val readerLetterSpacing: Float get() = settings.readerLetterSpacing
    val readerCustomFontPath: String get() = settings.readerCustomFontPath
    val readerCustomFontName: String get() = settings.readerCustomFontName
    val readerJustificationEnabled: Boolean get() = settings.readerJustificationEnabled
    val readerHyphenationEnabled: Boolean get() = settings.readerHyphenationEnabled
    val readerAmbientSyncEnabled: Boolean get() = settings.readerAmbientSyncEnabled

    // --- Customizable AI Prompts ---
    val glossaryPrompt: String get() = settings.glossaryPrompt
    val polishPrompt: String get() = settings.polishPrompt
    val recapPrompt: String get() = settings.recapPrompt

    // --- Multi-Select Mode for Library ---
    var isLibraryMultiSelectMode by mutableStateOf(false)
    var selectedLibraryBookIds by mutableStateOf<Set<String>>(emptySet())

    val aiRegistry = com.example.data.ai.AiProviderRegistry(application)
    val modelManager = com.example.data.ai.ModelManager(application)
    val aiFeatures = AiFeaturesManager(repository, aiRegistry, settings).apply {
        aggressiveCleanProvider = { aggressiveClean }
    }
    val scraping = ScrapingManager(
        application = application,
        repository = repository,
        settings = settings,
        coroutineScope = viewModelScope
    )
    val tts = TtsPlaybackManager(
        application = application,
        repository = repository,
        settings = settings,
        scraping = scraping,
        coroutineScope = viewModelScope
    )
    val library = LibraryManager(
        application = application,
        repository = repository,
        scrapingManager = scraping,
        onClearSelection = {
            selectedLibraryBookIds = emptySet()
            isLibraryMultiSelectMode = false
        }
    )
    val progress = ReadingProgressManager(application, repository, tts, viewModelScope)

    // --- Scraper State Delegations ---
    var failedChaptersList: List<MissingChapter>
        get() = scraping.failedChaptersList
        set(value) { scraping.failedChaptersList = value }

    var scrapeUrl: String
        get() = scraping.scrapeUrl
        set(value) { scraping.scrapeUrl = value }

    var scrapeBookName: String
        get() = scraping.scrapeBookName
        set(value) { scraping.scrapeBookName = value }

    var maxChaptersInput: String
        get() = scraping.maxChaptersInput
        set(value) { scraping.maxChaptersInput = value }

    var fromChapterInput: String
        get() = scraping.fromChapterInput
        set(value) { scraping.fromChapterInput = value }

    var toChapterInput: String
        get() = scraping.toChapterInput
        set(value) { scraping.toChapterInput = value }

    var selectedFormat: String
        get() = scraping.selectedFormat
        set(value) { scraping.selectedFormat = value }

    var aggressiveClean: Boolean
        get() = scraping.aggressiveClean
        set(value) { scraping.aggressiveClean = value }

    val scrapeLogs get() = scraping.scrapeLogs

    var isScraping: Boolean
        get() = scraping.isScraping
        private set(value) { scraping.isScraping = value }

    var isScrapePaused: Boolean
        get() = scraping.isScrapePaused
        private set(value) { scraping.isScrapePaused = value }

    var shouldSkipCurrentChapter: Boolean
        get() = scraping.shouldSkipCurrentChapter
        private set(value) { scraping.shouldSkipCurrentChapter = value }

    var isSearchingMissing: Boolean
        get() = scraping.isSearchingMissing
        private set(value) { scraping.isSearchingMissing = value }

    var missingChaptersToScrape: List<MissingChapter>
        get() = scraping.missingChaptersToScrape
        private set(value) { scraping.missingChaptersToScrape = value }

    var missingChaptersSummary: String
        get() = scraping.missingChaptersSummary
        private set(value) { scraping.missingChaptersSummary = value }

    var checkingNewChaptersBookId: String?
        get() = scraping.checkingNewChaptersBookId
        set(value) { scraping.checkingNewChaptersBookId = value }

    var isCheckingNewChapters: Boolean
        get() = scraping.isCheckingNewChapters
        set(value) { scraping.isCheckingNewChapters = value }

    var showNewChaptersDialog: Boolean
        get() = scraping.showNewChaptersDialog
        set(value) { scraping.showNewChaptersDialog = value }

    var newChaptersFoundCount: Int
        get() = scraping.newChaptersFoundCount
        set(value) { scraping.newChaptersFoundCount = value }

    var checkedBookEntity: BookEntity?
        get() = scraping.checkedBookEntity
        set(value) { scraping.checkedBookEntity = value }

    var newChaptersList: List<MissingChapter>
        get() = scraping.newChaptersList
        set(value) { scraping.newChaptersList = value }

    var scrapingStatus: String
        get() = scraping.scrapingStatus
        private set(value) { scraping.scrapingStatus = value }

    var currentChapterNum: Int
        get() = scraping.currentChapterNum
        private set(value) { scraping.currentChapterNum = value }

    var totalChaptersToScrape: Int
        get() = scraping.totalChaptersToScrape
        private set(value) { scraping.totalChaptersToScrape = value }

    var scrapeProgress: Float
        get() = scraping.scrapeProgress
        private set(value) { scraping.scrapeProgress = value }

    var showCaptchaDialog: Boolean
        get() = scraping.showCaptchaDialog
        set(value) { scraping.showCaptchaDialog = value }

    var captchaUrl: String
        get() = scraping.captchaUrl
        set(value) { scraping.captchaUrl = value }

    var showManualBrowser: Boolean
        get() = scraping.showManualBrowser
        set(value) { scraping.showManualBrowser = value }

    var manualBrowserUrl: String
        get() = scraping.manualBrowserUrl
        set(value) { scraping.manualBrowserUrl = value }

    var rescrapingChapterId: String?
        get() = scraping.rescrapingChapterId
        set(value) { scraping.rescrapingChapterId = value }

    var isRescrapingBookId: String?
        get() = scraping.isRescrapingBookId
        set(value) { scraping.isRescrapingBookId = value }

    var rescrapeBookProgress: Float
        get() = scraping.rescrapeBookProgress
        set(value) { scraping.rescrapeBookProgress = value }

    init {
        library.scheduleChapterUpdatesCheck()
        tts.onSpeakStarted = { isTtsPlayerBarMinimized = false }
    }

    // --- Stats state ---
    val totalBooks = repository.allBooks.map { it.size }
    val totalChapters = flow {
        emit(repository.getTotalChapterCount())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- TTS Minimize State ---
    var isTtsPlayerBarMinimized by mutableStateOf(false)

    // --- Text To Speech (TTS) System ---
    var isTtsReady: Boolean
        get() = tts.isTtsReady
        set(value) { tts.isTtsReady = value }

    var ttsVoices: List<VoiceOption>
        get() = tts.ttsVoices
        set(value) { tts.ttsVoices = value }

    var selectedVoiceId: String
        get() = tts.selectedVoiceId
        set(value) { tts.selectedVoiceId = value }

    var previewingVoiceId: String?
        get() = tts.previewingVoiceId
        set(value) { tts.previewingVoiceId = value }

    // TTS Playback state
    var ttsPlayingBook: BookEntity?
        get() = tts.ttsPlayingBook
        set(value) { tts.ttsPlayingBook = value }

    var ttsPlayingChapter: ChapterEntity?
        get() = tts.ttsPlayingChapter
        set(value) { tts.ttsPlayingChapter = value }

    var ttsIsPlaying: Boolean
        get() = tts.ttsIsPlaying
        set(value) { tts.ttsIsPlaying = value }

    var ttsIsPaused: Boolean
        get() = tts.ttsIsPaused
        set(value) { tts.ttsIsPaused = value }

    var ttsPitch: Float
        get() = tts.ttsPitch
        set(value) { tts.ttsPitch = value }

    var ttsSpeed: Float
        get() = tts.ttsSpeed
        set(value) { tts.ttsSpeed = value }

    var ttsActiveParagraphIndex: Int?
        get() = tts.ttsActiveParagraphIndex
        set(value) { tts.ttsActiveParagraphIndex = value }

    val sherpaOnnxTtsEngine: SherpaOnnxTtsEngine
        get() = tts.sherpaOnnxTtsEngine

    val premiumVoiceDownloading: Boolean
        get() = tts.premiumVoiceDownloading

    val premiumVoiceDownloadProgress: Int
        get() = tts.premiumVoiceDownloadProgress

    val premiumVoiceDownloadError: String?
        get() = tts.premiumVoiceDownloadError

    var sleepTimerMinutes: Int
        get() = tts.sleepTimerMinutes
        set(value) { tts.sleepTimerMinutes = value }

    var sleepTimerRemainingSeconds: Int?
        get() = tts.sleepTimerRemainingSeconds
        set(value) { tts.sleepTimerRemainingSeconds = value }


    private fun downloadCoverAndSaveMetadata(book: BookEntity, cookies: String): BookEntity {
        val context = getApplication<Application>().applicationContext
        val outputFolder = File(context.filesDir, book.id)
        if (!outputFolder.exists()) outputFolder.mkdirs()

        // 1. Download Cover Image
        var updatedBook = book
        val coverUrl = book.coverUrl
        if (!coverUrl.isNullOrEmpty()) {
            try {
                scraping.addLog("Downloading book cover from $coverUrl...")
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
                    scraping.addLog("Book cover downloaded successfully to: ${coverFile.name}")
                } else {
                    scraping.addLog("Failed to download book cover (HTTP $responseCode)")
                }
            } catch (e: Exception) {
                scraping.addLog("Error downloading book cover: ${e.message}")
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
            scraping.addLog("Saved metadata info.json for book: ${updatedBook.title}")
        } catch (e: Exception) {
            scraping.addLog("Error saving info.json: ${e.message}")
            e.printStackTrace()
        }

        return updatedBook
    }

    // --- Glossary AI State ---
    val isGeneratingGlossary: Boolean get() = aiFeatures.isGeneratingGlossary
    val glossaryStatusMessage: String get() = aiFeatures.glossaryStatusMessage

    // --- Translation Polish State ---
    val polishedChaptersLoading get() = aiFeatures.polishedChaptersLoading

    // --- Chapter Recap State ---
    private val _chapterRecapLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val chapterRecapLoading = _chapterRecapLoading.asStateFlow()

    // --- AI Novel Discovery State ---
    val discoveryItems: List<DiscoveryItem> get() = aiFeatures.discoveryItems
    val isDiscovering: Boolean get() = aiFeatures.isDiscovering
    val discoveryError: String get() = aiFeatures.discoveryError

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

    override fun onCleared() {
        super.onCleared()
        tts.unregister()
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



