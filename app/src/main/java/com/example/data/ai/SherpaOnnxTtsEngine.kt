package com.example.data.ai

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import kotlinx.coroutines.*
import java.io.File

class SherpaOnnxTtsEngine(private val context: Context) {

    private val modelManager = PiperModelManager(context)
    private var offlineTts: OfflineTts? = null
    private var loadedVoiceId: String? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var audioTrack: AudioTrack? = null
    
    @Volatile
    private var isPlaying = false
    
    var selectedVoiceId: String = PiperVoiceCatalog.AMY_LOW.id
    var selectedSpeakerId: Int = 0

    // Ensure the current voice engine is loaded and initialized
    private fun initEngine(voice: PiperVoice): OfflineTts? {
        if (offlineTts != null && loadedVoiceId == voice.id) {
            return offlineTts
        }
        
        // Release previous engine
        releaseEngine()
        
        if (!modelManager.isVoiceDownloaded(voice)) {
            return null
        }
        
        try {
            val voiceDir = modelManager.getVoiceModelDir(voice)
            val modelPath = File(voiceDir, voice.modelFilename).absolutePath
            val tokensPath = File(voiceDir, voice.tokensFilename).absolutePath
            val dataDir = File(voiceDir, "espeak-ng-data").absolutePath
            
            val modelConfig = if (voice.isKokoro) {
                val voicesPath = File(voiceDir, voice.voicesFilename).absolutePath
                val kokoroConfig = OfflineTtsKokoroModelConfig(
                    model = modelPath,
                    voices = voicesPath,
                    tokens = tokensPath,
                    dataDir = dataDir,
                    lengthScale = 1.0f
                )
                OfflineTtsModelConfig(
                    kokoro = kokoroConfig,
                    numThreads = 2,
                    provider = "cpu"
                )
            } else {
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    tokens = tokensPath,
                    dataDir = dataDir,
                    lengthScale = 1.0f // Speed is controlled at generation time
                )
                OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    provider = "cpu"
                )
            }
            
            val config = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 1
            )
            
            offlineTts = OfflineTts(config = config)
            loadedVoiceId = voice.id
            return offlineTts
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun releaseEngine() {
        try {
            offlineTts?.release()
        } catch (e: Exception) {
            // ignore
        }
        offlineTts = null
        loadedVoiceId = null
    }

    fun isModelDownloaded(): Boolean {
        val voice = PiperVoiceCatalog.getVoiceById(selectedVoiceId)
        return modelManager.isVoiceDownloaded(voice)
    }

    suspend fun downloadModel(
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val voice = PiperVoiceCatalog.getVoiceById(selectedVoiceId)
        val result = modelManager.downloadAndExtractVoice(voice, onProgress)
        if (result.isSuccess) {
            onSuccess()
        } else {
            onFailure(result.exceptionOrNull()?.message ?: "Failed to download model")
        }
    }

    fun deleteModel(): Boolean {
        val voice = PiperVoiceCatalog.getVoiceById(selectedVoiceId)
        releaseEngine()
        return modelManager.deleteVoice(voice)
    }

    fun initOnnx() {
        // Automatically initialized on speak, or pre-warmed here
        val voice = PiperVoiceCatalog.getVoiceById(selectedVoiceId)
        initEngine(voice)
    }

    fun speak(
        text: String,
        speed: Float,
        pitch: Float, // Pitch is ignored as Piper uses lengthScale for speed
        onStart: (Int) -> Unit,
        onDone: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val oldJob = activeJob
        isPlaying = false
        oldJob?.cancel()
        
        isPlaying = true
        activeJob = scope.launch {
            try {
                oldJob?.join()
            } catch (e: Exception) {
                // ignore
            }
            
            val voice = PiperVoiceCatalog.getVoiceById(selectedVoiceId)
            val tts = initEngine(voice)
            if (tts == null) {
                withContext(Dispatchers.Main) {
                    onError?.invoke("Model not downloaded or failed to load")
                }
                return@launch
            }
            
            // Split paragraphs to keep latency low and allow cancellation per paragraph
            val paragraphs = text.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            try {
                for (idx in paragraphs.indices) {
                    if (!isPlaying || !isActive) break
                    
                    // Callback that paragraph is starting
                    withContext(Dispatchers.Main) {
                        onStart(idx)
                    }
                    
                    val paraText = paragraphs[idx]
                    
                    // Generate audio for the paragraph
                    // Note: lengthScale (speed) works inversely: 1.0/speed
                    val lengthScale = if (speed > 0) 1.0f / speed else 1.0f
                    val audio = tts.generate(
                        text = paraText,
                        sid = if (voice.isKokoro) voice.kokoroSpeakerId else selectedSpeakerId,
                        speed = lengthScale
                    )
                    
                    if (!isPlaying || !isActive) break
                    
                    // Play the generated audio samples
                    playSamples(audio.samples, audio.sampleRate)
                }
                
                if (isPlaying && isActive) {
                    withContext(Dispatchers.Main) {
                        onDone()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(e.message ?: "Speech generation failed")
                    }
                }
            } finally {
                isPlaying = false
            }
        }
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        
        val bufferSize = maxOf(minBufferSize, samples.size * 4)
        
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        
        synchronized(this@SherpaOnnxTtsEngine) {
            audioTrack = track
        }
        
        try {
            track.play()
            
            // Write audio samples in small chunks to support fast stop/interruption
            val chunkSize = 8192
            var offset = 0
            while (offset < samples.size && isPlaying && isActive) {
                val len = minOf(chunkSize, samples.size - offset)
                track.write(samples, offset, len, AudioTrack.WRITE_BLOCKING)
                offset += len
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                // ignore
            }
            synchronized(this@SherpaOnnxTtsEngine) {
                if (audioTrack == track) {
                    audioTrack = null
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        activeJob?.cancel()
        activeJob = null
        
        synchronized(this) {
            try {
                audioTrack?.stop()
                audioTrack?.flush()
                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun shutdown() {
        stop()
        releaseEngine()
    }
}
