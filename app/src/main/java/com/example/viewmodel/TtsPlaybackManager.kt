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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.*
import com.example.data.repository.NovelRepository
import com.example.data.ai.SherpaOnnxTtsEngine
import com.example.data.ai.PiperVoice
import com.example.data.ai.PiperVoiceCatalog
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class TtsPlaybackManager(
    private val application: Application,
    private val repository: NovelRepository,
    private val settings: SettingsManager,
    private val scraping: ScrapingManager,
    private val coroutineScope: CoroutineScope
) {
    private val prefs = application.getSharedPreferences("novel_hoarder_prefs", Context.MODE_PRIVATE)

    var focusModeEnabled by mutableStateOf(false)
    var ttsAutoScrollEnabled by mutableStateOf(true)
    var ttsTotalParagraphs by mutableStateOf(0)

    // --- Resumable session state & progress persistence (Declared here to avoid initialization order NPE in init block) ---
    var hasResumableSession by mutableStateOf(false)
    var resumeBookId by mutableStateOf("")
    var resumeChapterId by mutableStateOf("")
    var resumeParagraph by mutableStateOf(0)
    var resumeBookName by mutableStateOf("")
    var resumeChapterTitle by mutableStateOf("")

    // Text To Speech (TTS) System state
    private var tts: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
    var ttsVoices by mutableStateOf<List<VoiceOption>>(emptyList())
    var selectedVoiceId by mutableStateOf<String>("")
    var previewingVoiceId by mutableStateOf<String?>(null)

    // TTS Playback state
    var ttsPlayingBook by mutableStateOf<BookEntity?>(null)
    var ttsPlayingChapter by mutableStateOf<ChapterEntity?>(null)
    var ttsIsPlaying by mutableStateOf(false)
    var ttsIsPaused by mutableStateOf(false)

    var ttsPitch by mutableStateOf(1.0f)
    var ttsSpeed by mutableStateOf(1.0f)
    var ttsActiveParagraphIndex by mutableStateOf<Int?>(-1)

    // Premium Piper offline voice properties
    val sherpaOnnxTtsEngine = SherpaOnnxTtsEngine(application)
    var premiumVoiceDownloading by mutableStateOf(false)
        private set
    var premiumVoiceDownloadProgress by mutableStateOf(0)
        private set
    var premiumVoiceDownloadError by mutableStateOf<String?>(null)
        private set

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

    private val channelId = "tts_player_channel"
    private val notificationId = 1001

    private var mediaSession: MediaSessionCompat? = null

    init {
        // Load preferences
        focusModeEnabled = prefs.getBoolean("focus_mode", false)
        ttsAutoScrollEnabled = prefs.getBoolean("tts_auto_scroll", true)

        registerTtsReceiver()
        loadResumableTtsSession()
    }

    fun unregister() {
        unregisterTtsReceiver()
        sleepTimerJob?.cancel()
        sherpaOnnxTtsEngine.stop()
        sherpaOnnxTtsEngine.shutdown()
        tts?.stop()
        tts?.shutdown()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
    }

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
        coroutineScope.launch(Dispatchers.Main) {
            sherpaOnnxTtsEngine.downloadModel(
                onProgress = { progress ->
                    premiumVoiceDownloadProgress = progress
                },
                onSuccess = {
                    premiumVoiceDownloading = false
                    val prefix = if (voice.isKokoro) "Kokoro" else "Piper"
                    setTtsVoice(VoiceOption(voice.id, "$prefix - ${voice.name}", Locale.US))
                    initTts()
                    addLog("$prefix Voice (${voice.name}) downloaded successfully.")
                },
                onFailure = { error ->
                    premiumVoiceDownloadError = error
                    premiumVoiceDownloading = false
                    val prefix = if (voice.isKokoro) "Kokoro" else "Piper"
                    addLog("Error downloading $prefix Voice (${voice.name}): $error")
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
            val prefix = if (voice.isKokoro) "Kokoro" else "Piper"
            addLog("$prefix Voice (${voice.name}) model deleted.")
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

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes
        if (minutes <= 0) {
            sleepTimerRemainingSeconds = null
            return
        }
        sleepTimerRemainingSeconds = minutes * 60
        sleepTimerJob = coroutineScope.launch(Dispatchers.Default) {
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

    fun initMediaSession() {
        if (mediaSession == null) {
            val context = application.applicationContext
            mediaSession = MediaSessionCompat(context, "NovelHoarderTTS").apply {
                setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        coroutineScope.launch(Dispatchers.Main) {
                            resumeTts()
                        }
                    }

                    override fun onPause() {
                        coroutineScope.launch(Dispatchers.Main) {
                            pauseTts()
                        }
                    }

                    override fun onSkipToNext() {
                        coroutineScope.launch(Dispatchers.Main) {
                            playNextChapterTts()
                        }
                    }

                    override fun onSkipToPrevious() {
                        coroutineScope.launch(Dispatchers.Main) {
                            playPreviousChapterTts()
                        }
                    }

                    override fun onStop() {
                        coroutineScope.launch(Dispatchers.Main) {
                            stopTts()
                        }
                    }

                    override fun onSeekTo(pos: Long) {
                        coroutineScope.launch(Dispatchers.Main) {
                            val paraIndex = (pos / 1000L).toInt()
                            val total = ttsTotalParagraphs
                            if (paraIndex in 0 until total) {
                                val book = ttsPlayingBook
                                val chapter = ttsPlayingChapter
                                if (book != null && chapter != null) {
                                    speak(chapter.content, book, chapter, startFromParagraphIndex = paraIndex)
                                }
                            }
                        }
                    }
                })
                isActive = true
            }
        }
    }

    fun updatePlaybackState() {
        initMediaSession()
        val state = if (ttsIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        val para = ttsActiveParagraphIndex ?: 0
        val total = ttsTotalParagraphs
        val currentPositionMs = if (para >= 0) para * 1000L else 0L

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, currentPositionMs, 1.0f)

        mediaSession?.setPlaybackState(stateBuilder.build())

        val book = ttsPlayingBook
        val chapter = ttsPlayingChapter

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapter?.title ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, book?.title ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book?.author ?: "Unknown Author")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, total * 1000L)

        if (total > 0 && para >= 0) {
            val progressSubtitle = "Paragraph ${para + 1} of $total"
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, progressSubtitle)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, book?.author ?: "Unknown Author")
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    fun showTtsNotification() {
        val context = application.applicationContext
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return

        updatePlaybackState()

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

        val para = ttsActiveParagraphIndex ?: 0
        val total = ttsTotalParagraphs
        val contentText = if (total > 0 && para >= 0) {
            "${chapter.title} • Paragraph ${para + 1} of $total"
        } else {
            chapter.title
        }

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle(book.title)
            .setContentText(contentText)
            .setOngoing(ttsIsPlaying)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(android.R.drawable.ic_media_previous, "Prev Chapter", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next Chapter", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun dismissTtsNotification() {
        val context = application.applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun registerTtsReceiver() {
        if (!isReceiverRegistered) {
            val context = application.applicationContext
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
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    fun unregisterTtsReceiver() {
        if (isReceiverRegistered) {
            val context = application.applicationContext
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
        tts = TextToSpeech(application) { status ->
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
                    val prefix = if (voice.isKokoro) "Kokoro" else "Piper"
                    VoiceOption(voice.id, "$prefix - ${voice.name}", Locale.US)
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

    fun stopVoicePreview() {
        sherpaOnnxTtsEngine.stop()
        tts?.stop()
        previewingVoiceId = null
    }

    fun playVoicePreview(voiceOption: VoiceOption) {
        val cleanName = voiceOption.name.replace("Piper - ", "").replace("Kokoro - ", "").replace("System: ", "")
        val sampleText = "Hello! This is a sample of the $cleanName voice."
        val isSherpa = voiceOption.id.startsWith("vits-piper-") || voiceOption.id.startsWith("kokoro-")
        val piperVoice = if (isSherpa) com.example.data.ai.PiperVoiceCatalog.getVoiceById(voiceOption.id) else null
        val isDownloaded = piperVoice != null && isVoiceDownloaded(piperVoice)

        stopVoicePreview()

        if (isSherpa && isDownloaded) {
            coroutineScope.launch(Dispatchers.Main) {
                previewingVoiceId = voiceOption.id
                sherpaOnnxTtsEngine.selectedVoiceId = voiceOption.id
                sherpaOnnxTtsEngine.selectedSpeakerId = getSpeakerId(voiceOption.id)
                sherpaOnnxTtsEngine.initOnnx()
                sherpaOnnxTtsEngine.speak(
                    text = sampleText,
                    speed = ttsSpeed,
                    pitch = ttsPitch,
                    onStart = {
                        coroutineScope.launch(Dispatchers.Main) {
                            previewingVoiceId = voiceOption.id
                        }
                    },
                    onDone = {
                        coroutineScope.launch(Dispatchers.Main) {
                            if (previewingVoiceId == voiceOption.id) {
                                previewingVoiceId = null
                            }
                        }
                    },
                    onError = { _ ->
                        coroutineScope.launch(Dispatchers.Main) {
                            if (previewingVoiceId == voiceOption.id) {
                                previewingVoiceId = null
                            }
                        }
                    }
                )
            }
        } else if (!isSherpa) {
            initTts {
                coroutineScope.launch(Dispatchers.Main) {
                    previewingVoiceId = voiceOption.id
                    val rawVoices = tts?.voices
                    val actualVoice = rawVoices?.find { it.name == voiceOption.id }
                    if (actualVoice != null) {
                        tts?.setVoice(actualVoice)
                    } else {
                        tts?.setLanguage(voiceOption.locale)
                    }
                    tts?.setPitch(ttsPitch)
                    tts?.setSpeechRate(ttsSpeed)

                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            coroutineScope.launch(Dispatchers.Main) {
                                previewingVoiceId = voiceOption.id
                            }
                        }
                        override fun onDone(utteranceId: String?) {
                            coroutineScope.launch(Dispatchers.Main) {
                                if (previewingVoiceId == voiceOption.id) {
                                    previewingVoiceId = null
                                }
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            coroutineScope.launch(Dispatchers.Main) {
                                if (previewingVoiceId == voiceOption.id) {
                                    previewingVoiceId = null
                                }
                            }
                        }
                    })

                    tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "preview_${voiceOption.id}")
                }
            }
        }
    }

    fun setTtsVoice(voiceOption: VoiceOption) {
        selectedVoiceId = voiceOption.id
        prefs.edit().putString("tts_selected_voice", voiceOption.id).apply()
        if (voiceOption.id.startsWith("vits-piper-") || voiceOption.id.startsWith("kokoro-")) {
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
        coroutineScope.launch(Dispatchers.IO) {
            val updatedBook = book.copy(lastReadChapterId = chapter.id)
            repository.updateBook(updatedBook)
            repository.updateChapterReadStatus(chapter.id, true)
        }

        val isSherpa = selectedVoiceId.startsWith("vits-piper-") || selectedVoiceId.startsWith("kokoro-")
        val piperVoice = if (isSherpa) PiperVoiceCatalog.getVoiceById(selectedVoiceId) else null
        val isDownloaded = piperVoice != null && isVoiceDownloaded(piperVoice)

        if (isSherpa && isDownloaded) {
            coroutineScope.launch(Dispatchers.Main) {
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
                        coroutineScope.launch(Dispatchers.Main) {
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
                        coroutineScope.launch(Dispatchers.Main) {
                            playNextChapterTts()
                        }
                    },
                    onError = { errorMsg ->
                        coroutineScope.launch(Dispatchers.Main) {
                            addLog("Piper TTS Error: $errorMsg")
                            stopTts()
                        }
                    }
                )

                showTtsNotification()
            }
            return
        }

        ttsPlayingBook = book
        ttsPlayingChapter = chapter
        ttsIsPlaying = true
        ttsIsPaused = false

        initTts {
            coroutineScope.launch(Dispatchers.Main) {
                if (!ttsIsPlaying || ttsPlayingChapter?.id != chapter.id) {
                    return@launch
                }

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
                                    coroutineScope.launch(Dispatchers.Main) {
                                        ttsActiveParagraphIndex = idx
                                        saveTtsProgress()
                                        loadResumableTtsSession()
                                    }
                                }
                            } else if (utteranceId.startsWith("title_")) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    ttsActiveParagraphIndex = -1
                                    saveTtsProgress()
                                    loadResumableTtsSession()
                                }
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId.startsWith("para_${chapter.id}_${rawParagraphs.size - 1}")) {
                            coroutineScope.launch(Dispatchers.Main) {
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
        if (!ttsIsPlaying) return
        val book = ttsPlayingBook ?: return
        val currentChapter = ttsPlayingChapter ?: return

        coroutineScope.launch(Dispatchers.IO) {
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

        coroutineScope.launch(Dispatchers.IO) {
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
        sherpaOnnxTtsEngine.stop()
        tts?.stop()
        ttsIsPlaying = false
        ttsIsPaused = true
        showTtsNotification()
        saveTtsProgress()
        loadResumableTtsSession()
    }

    fun resumeTts() {
        val book = ttsPlayingBook ?: return
        val chapter = ttsPlayingChapter ?: return
        speak(chapter.content, book, chapter, startFromParagraphIndex = ttsActiveParagraphIndex ?: 0)
    }

    fun stopTts() {
        sherpaOnnxTtsEngine.stop()
        tts?.stop()
        ttsPlayingBook = null
        ttsPlayingChapter = null
        ttsIsPlaying = false
        ttsIsPaused = false
        ttsActiveParagraphIndex = -1
        dismissTtsNotification()
        clearTtsProgress()
        hasResumableSession = false

        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
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

        // Sync with reading progress
        saveReadingProgress(book.id, chapter.id, if (para < 0) 0 else para)

        // Make sure the lastReadChapterId matches
        if (book.lastReadChapterId != chapter.id) {
            coroutineScope.launch(Dispatchers.IO) {
                repository.updateBook(book.copy(lastReadChapterId = chapter.id))
            }
        }

        // Keep system notification and MediaSession API updated in real time as paragraphs change
        showTtsNotification()
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
            coroutineScope.launch(Dispatchers.IO) {
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
            coroutineScope.launch(Dispatchers.IO) {
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

    fun saveReadingProgress(bookId: String, chapterId: String, paragraphIndex: Int) {
        prefs.edit()
            .putInt("progress_para_${bookId}_${chapterId}", paragraphIndex)
            .putString("progress_chapter_${bookId}", chapterId)
            .apply()
    }

    fun addLog(msg: String) {
        scraping.addLog(msg)
    }
}
