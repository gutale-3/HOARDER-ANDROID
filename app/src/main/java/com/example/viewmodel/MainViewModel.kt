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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.NovelRepository
import com.example.ui.theme.AppTheme
import com.example.util.CloudflareException
import com.example.util.NovelCompiler
import com.example.util.TomatoScraper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = NovelRepository(database.bookDao())

    // --- Global Theme & Preferences ---
    private val prefs = application.getSharedPreferences("novel_hoarder_prefs", Context.MODE_PRIVATE)

    var currentTheme by mutableStateOf(AppTheme.IMMERSIVE_UI)
        private set

    var readerFontSize by mutableStateOf(16)
        private set

    var defaultUserAgent by mutableStateOf("")
        private set

    init {
        val themeName = prefs.getString("selected_theme", AppTheme.IMMERSIVE_UI.name)
        currentTheme = AppTheme.valueOf(themeName ?: AppTheme.IMMERSIVE_UI.name)
        readerFontSize = prefs.getInt("reader_font_size", 18)
        defaultUserAgent = prefs.getString("user_agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36") ?: ""
        registerTtsReceiver()
    }

    fun updateTheme(theme: AppTheme) {
        currentTheme = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun updateFontSize(size: Int) {
        readerFontSize = size.coerceIn(12, 36)
        prefs.edit().putInt("reader_font_size", readerFontSize).apply()
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
    var scrapingStatus by mutableStateOf("● Idle")
        private set
    var currentChapterNum by mutableStateOf(0)
        private set
    var totalChaptersToScrape by mutableStateOf(0)
        private set
    var scrapeProgress by mutableStateOf(0f)
        private set

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
                    scrapeProgress = 0f
                }
            }
        }
    }

    fun stopScraping() {
        addLog("Stopping scrape session...")
        scrapingStatus = "● Stopping..."
        scrapeJob?.cancel()
        captchaContinuation?.cancel()
        isScraping = false
    }

    private suspend fun runScraperLoop(url: String, bookName: String) {
        val bookId = TomatoScraper.parseBookId(url) ?: "novel_${System.currentTimeMillis()}"
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
                    bookEntity = TomatoScraper.scrapeBookInfo(webView, bookUrl)
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
                chapterUrls = TomatoScraper.scrapeChapterList(webView, bookUrl)
            } catch (e: Exception) {
                addLog("TOC parsing failed, using direct scraping if possible: ${e.message}")
            }

            if (chapterUrls.isEmpty()) {
                addLog("No chapter list found. If you pasted a chapter URL directly, we will try standard crawling.")
            } else {
                addLog("TOC loaded: ${chapterUrls.size} chapters found.")
            }

            // Determine starting indices
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

            totalChaptersToScrape = filteredUrls.size
            addLog("Preparing to scrape $totalChaptersToScrape chapters...")

            var sessionDownloadedCount = 0
            val glossaries = repository.getGlossary(currentBookState.id)

            for ((index, chapterUrl) in filteredUrls.withIndex()) {
                if (!isScraping) break

                currentChapterNum = index + 1
                scrapeProgress = currentChapterNum.toFloat() / totalChaptersToScrape
                scrapingStatus = "● Downloading ($currentChapterNum/$totalChaptersToScrape)..."

                val chapId = TomatoScraper.parseChapterId(chapterUrl) ?: "ch_$currentChapterNum"
                val fullChapId = "${currentBookState.id}_$chapId"

                // Check if already downloaded locally
                val existing = repository.getChapter(fullChapId)
                if (existing != null && existing.content.length > 100) {
                    addLog("Chapter ${index + fromCap} already downloaded. Skipping.")
                    continue
                }

                var downloadSuccess = false
                var tries = 0

                while (!downloadSuccess && tries < 3 && isScraping) {
                    try {
                        val rawContent = TomatoScraper.scrapeChapterContent(webView, chapterUrl)
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
                            chapterNumber = index + fromCap,
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
                        addLog("Error on chapter $currentChapterNum, retry $tries/3: ${e.message}")
                        delay(3000)
                    }
                }

                // Simple random delay to respect scraping etiquette and prevent bans (just like desktop engine!)
                if (isScraping && index < filteredUrls.size - 1) {
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
                val outputFolder = File(context.filesDirs, currentBookState.id)
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

    // --- Book Deletion and Export Helpers ---
    fun deleteBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete local folders if any exist
                val context = getApplication<Application>().applicationContext
                val folder = File(context.filesDirs, bookId)
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
            val outputFolder = File(context.filesDirs, book.id)
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
                addAction("com.example.ACTION_NEXT_CHAPTER")
                addAction("com.example.ACTION_STOP_TTS")
            }
            context.registerReceiver(ttsReceiver, filter)
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
                
                ttsVoices = availableVoices.distinctBy { it.id }

                val savedVoice = prefs.getString("tts_selected_voice", "") ?: ""
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

    fun setTtsVoice(voiceOption: VoiceOption) {
        selectedVoiceId = voiceOption.id
        prefs.edit().putString("tts_selected_voice", voiceOption.id).apply()
        if (voiceOption.id.startsWith("default_")) {
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

    fun speak(text: String, book: BookEntity, chapter: ChapterEntity, startFromParagraphIndex: Int = -1) {
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
                                    }
                                }
                            } else if (utteranceId.startsWith("title_")) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    ttsActiveParagraphIndex = -1
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

    fun pauseTts() {
        if (ttsIsPlaying) {
            tts?.stop()
            ttsIsPlaying = false
            ttsIsPaused = true
            showTtsNotification()
        }
    }

    fun resumeTts() {
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return
        speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
    }

    fun stopTts() {
        tts?.stop()
        ttsPlayingBook = null
        ttsPlayingChapter = null
        ttsIsPlaying = false
        ttsIsPaused = false
        ttsActiveParagraphIndex = -1
        dismissTtsNotification()
    }

    private fun downloadCoverAndSaveMetadata(book: BookEntity, cookies: String): BookEntity {
        val context = getApplication<Application>().applicationContext
        val outputFolder = File(context.filesDirs, book.id)
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

    override fun onCleared() {
        super.onCleared()
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

// Simple extension helper
val Context.filesDirs: File
    get() = this.filesDir
