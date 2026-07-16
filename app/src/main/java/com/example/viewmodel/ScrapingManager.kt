package com.example.viewmodel

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import com.example.data.repository.NovelRepository
import com.example.data.scraper.SourceManager
import com.example.util.TomatoScraper
import com.example.util.CloudflareException
import com.example.util.NovelCompiler
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class MissingChapter(
    val url: String,
    val chapterNumber: Int
)

class ScrapingManager(
    private val application: Application,
    private val repository: NovelRepository,
    private val settings: SettingsManager,
    private val coroutineScope: CoroutineScope
) {
    // --- Scraper State ---
    var scrapeUrl by mutableStateOf("")
    var scrapeBookName by mutableStateOf("")
    var maxChaptersInput by mutableStateOf("")
    var fromChapterInput by mutableStateOf("")
    var toChapterInput by mutableStateOf("")
    var selectedFormat by mutableStateOf("Both") // EPUB, PDF, Both, Database only
    var aggressiveClean by mutableStateOf(false)
    var failedChaptersList by mutableStateOf<List<MissingChapter>>(emptyList())

    // Live terminal log output (just like the desktop version!)
    private val _scrapeLogs = MutableStateFlow<List<String>>(emptyList())
    val scrapeLogs = _scrapeLogs.asStateFlow()

    var isScraping by mutableStateOf(false)
        internal set
    var isScrapePaused by mutableStateOf(false)
        internal set
    var shouldSkipCurrentChapter by mutableStateOf(false)
        internal set

    // Missing chapters variables
    var isSearchingMissing by mutableStateOf(false)
        internal set
    var missingChaptersToScrape by mutableStateOf<List<MissingChapter>>(emptyList())
        internal set
    var missingChaptersSummary by mutableStateOf("")
        internal set

    // --- Check for New Chapters State ---
    var checkingNewChaptersBookId by mutableStateOf<String?>(null)
    var isCheckingNewChapters by mutableStateOf(false)
    var showNewChaptersDialog by mutableStateOf(false)
    var newChaptersFoundCount by mutableStateOf(0)
    var checkedBookEntity by mutableStateOf<BookEntity?>(null)
    var newChaptersList by mutableStateOf<List<MissingChapter>>(emptyList())

    var scrapingStatus by mutableStateOf("● Idle")
        internal set
    var currentChapterNum by mutableStateOf(0)
        internal set
    var totalChaptersToScrape by mutableStateOf(0)
        internal set
    var scrapeProgress by mutableStateOf(0f)
        internal set

    // CAPTCHA verification variables
    var showCaptchaDialog by mutableStateOf(false)
    var captchaUrl by mutableStateOf("")
    private var captchaContinuation: CancellableContinuation<Unit>? = null

    // Manual Interactive Browser variables
    var showManualBrowser by mutableStateOf(false)
    var manualBrowserUrl by mutableStateOf("https://tomatomtl.com")

    // --- Single Chapter and Novel Rescraping Logic ---
    var rescrapingChapterId by mutableStateOf<String?>(null)
    var isRescrapingBookId by mutableStateOf<String?>(null)
    var rescrapeBookProgress by mutableStateOf(0f)

    private val defaultUserAgent: String get() = settings.defaultUserAgent
    private var scrapeJob: Job? = null

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

        scrapeJob = coroutineScope.launch(Dispatchers.IO) {
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
        
        coroutineScope.launch(Dispatchers.IO) {
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
                WebView(application.applicationContext).apply {
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

        coroutineScope.launch(Dispatchers.IO) {
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
                    WebView(application.applicationContext).apply {
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

        scrapeJob = coroutineScope.launch(Dispatchers.IO) {
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

        scrapeJob = coroutineScope.launch(Dispatchers.IO) {
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
            WebView(application.applicationContext).apply {
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

                val context = application.applicationContext
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

    fun rescrapeSingleChapter(chapter: ChapterEntity, onComplete: (Boolean, String) -> Unit) {
        if (rescrapingChapterId != null) {
            onComplete(false, "Already rescraping another chapter.")
            return
        }
        rescrapingChapterId = chapter.id
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val scraper = SourceManager.getSourceForUrl(chapter.url)
                val webView = withContext(Dispatchers.Main) {
                    WebView(application.applicationContext).apply {
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
        
        coroutineScope.launch(Dispatchers.IO) {
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
                    WebView(application.applicationContext).apply {
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

    private fun downloadCoverAndSaveMetadata(book: BookEntity, cookies: String): BookEntity {
        val context = application.applicationContext
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
}
