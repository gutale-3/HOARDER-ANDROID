package com.example.data.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Real offline neural TTS engine backed by sherpa-onnx (Piper / VITS models).
 *
 * Multiple voices can be installed side by side under `filesDir/piper/<folder>`.
 * Synthesis is performed with [OfflineTts.generate] and streamed to an
 * [AudioTrack]. If the native `libsherpa-onnx-jni.so` is not bundled (see
 * app/libs/README-sherpa.md) any attempt to load a model throws and
 * [isNativeAvailable] returns false, letting the caller fall back to the system
 * TextToSpeech engine.
 */
class SherpaOnnxTtsEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val piperRoot: File by lazy { File(context.filesDir, "piper").apply { mkdirs() } }

    private var tts: OfflineTts? = null
    private var loadedVoiceId: String? = null
    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var stopRequested = false

    /** Null until we first try to load a native model; then true/false. */
    var nativeAvailable: Boolean? = null
        private set

    // ---------------------------------------------------------------- catalog
    private fun voiceDir(voice: PiperVoice) = File(piperRoot, voice.folderName)

    fun isVoiceInstalled(voice: PiperVoice): Boolean {
        val model = File(voiceDir(voice), voice.modelFileName)
        val tokens = File(voiceDir(voice), "tokens.txt")
        return model.exists() && model.length() > 0 && tokens.exists()
    }

    fun installedVoices(): List<PiperVoice> =
        PiperVoiceCatalog.voices.filter { isVoiceInstalled(it) }

    fun anyVoiceInstalled(): Boolean = installedVoices().isNotEmpty()

    // -------------------------------------------------------------- download
    /**
     * Streams the voice's `.tar.bz2`, extracts it under `filesDir/piper/`, and
     * reports progress 0..100. Runs on the caller's (background) thread.
     */
    fun downloadVoice(
        voice: PiperVoice,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val archive = File(piperRoot, "${voice.folderName}.tar.bz2")
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(voice.tarUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onFailure("Download failed: HTTP ${response.code}")
                    return
                }
                val body = response.body ?: run { onFailure("Empty response body"); return }
                val total = body.contentLength().takeIf { it > 0 } ?: (voice.approxSizeMb * 1_000_000L)
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var read: Int
                        var lastPct = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 90) / total).toInt().coerceIn(0, 90)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }

            onProgress(92)
            extractTarBz2(archive, piperRoot)
            archive.delete()

            if (isVoiceInstalled(voice)) {
                onProgress(100)
                onSuccess()
            } else {
                onFailure("Extraction finished but model files are missing")
            }
        } catch (e: Throwable) {
            archive.delete()
            onFailure(e.message ?: "Unknown download error")
        }
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        archive.inputStream().use { fis ->
            BZip2CompressorInputStream(BufferedInputStream(fis)).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }

    fun deleteVoice(voice: PiperVoice): Boolean {
        if (loadedVoiceId == voice.voiceId) unload()
        return voiceDir(voice).deleteRecursively()
    }

    // ------------------------------------------------------------ synthesis
    private fun ensureLoaded(voice: PiperVoice): Boolean {
        if (loadedVoiceId == voice.voiceId && tts != null) return true
        unload()
        return try {
            val dir = voiceDir(voice)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, voice.modelFileName).absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").absolutePath
                    ),
                    numThreads = 2,
                    debug = false
                )
            )
            tts = OfflineTts(config = config)
            loadedVoiceId = voice.voiceId
            nativeAvailable = true
            true
        } catch (t: Throwable) {
            // Native lib missing / model incompatible → fall back to system TTS.
            nativeAvailable = false
            tts = null
            loadedVoiceId = null
            false
        }
    }

    /** True once we know the native library is usable on this device. */
    fun isNativeAvailable(): Boolean = nativeAvailable == true

    /**
     * Speaks [paragraphs] starting at [startIndex]. [onParagraphStart] is invoked
     * (on the main thread) with the ABSOLUTE paragraph index just before each is
     * synthesized; [onDone] fires after the last. Returns false immediately if the
     * native engine cannot be used, so the caller can fall back.
     */
    fun speak(
        voice: PiperVoice,
        paragraphs: List<String>,
        startIndex: Int,
        speed: Float,
        onParagraphStart: (Int) -> Unit,
        onDone: () -> Unit
    ): Boolean {
        if (!ensureLoaded(voice)) return false
        val engine = tts ?: return false

        stop()
        stopRequested = false

        worker = Thread {
            try {
                val sampleRate = engine.sampleRate()
                initAudioTrack(sampleRate)
                audioTrack?.play()

                val begin = startIndex.coerceAtLeast(0)
                for (idx in begin until paragraphs.size) {
                    if (stopRequested) break
                    val para = paragraphs[idx]
                    if (para.isBlank()) continue
                    mainHandler.post { onParagraphStart(idx) }
                    val audio = engine.generate(text = para, sid = 0, speed = speed)
                    if (stopRequested) break
                    writeSamples(audio.samples)
                }
                if (!stopRequested) mainHandler.post { onDone() }
            } catch (t: Throwable) {
                nativeAvailable = false
            } finally {
                releaseAudioTrack()
            }
        }.also { it.start() }
        return true
    }

    private fun initAudioTrack(sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun writeSamples(samples: FloatArray) {
        val track = audioTrack ?: return
        var offset = 0
        while (offset < samples.size && !stopRequested) {
            val count = minOf(2048, samples.size - offset)
            val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (_: Throwable) {
        }
        audioTrack = null
    }

    fun stop() {
        stopRequested = true
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Throwable) {
        }
        worker?.let { if (it.isAlive) it.interrupt() }
        worker = null
        releaseAudioTrack()
    }

    private fun unload() {
        stop()
        try {
            tts?.release()
        } catch (_: Throwable) {
        }
        tts = null
        loadedVoiceId = null
    }

    fun shutdown() {
        unload()
    }

    // ----------------------------------------------- legacy compatibility
    // Older callers referenced a single "premium" model. Keep thin shims so the
    // rest of the code compiles while migrating to the multi-voice catalog.
    @Deprecated("Use installedVoices()/isVoiceInstalled()")
    fun isModelDownloaded(): Boolean = anyVoiceInstalled()

    @Deprecated("Use deleteVoice(voice)")
    fun deleteModel(): Boolean {
        var any = false
        PiperVoiceCatalog.voices.forEach { if (isVoiceInstalled(it)) any = deleteVoice(it) || any }
        return any
    }
}
