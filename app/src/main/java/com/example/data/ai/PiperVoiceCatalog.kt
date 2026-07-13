package com.example.data.ai

/**
 * A single downloadable offline neural voice (Piper / VITS) served by sherpa-onnx.
 *
 * Each entry corresponds to one `vits-piper-*.tar.bz2` archive published on the
 * sherpa-onnx "tts-models" GitHub release. The archive extracts to a directory
 * (`folderName`) containing `<modelFileName>`, `tokens.txt` and an
 * `espeak-ng-data/` folder — exactly what [SherpaOnnxTtsEngine] needs to build an
 * `OfflineTtsVitsModelConfig`.
 */
data class PiperVoice(
    val id: String,            // stable id, e.g. "en_US-amy-low"
    val displayName: String,   // shown in the picker
    val language: String,      // e.g. "English (US)"
    val quality: String,       // "low" | "medium" | "high"
    val approxSizeMb: Int,     // download size estimate for the UI
    val tarUrl: String,        // .tar.bz2 archive URL
    val folderName: String,    // directory created after extraction
    val modelFileName: String  // the .onnx file inside folderName
) {
    val voiceId: String get() = "piper_$id"
}

/**
 * Curated list of real Piper voices. URLs point at the official sherpa-onnx
 * release assets; downloads are streamed and extracted on device (see
 * [SherpaOnnxTtsEngine.downloadVoice]). Add or trim entries freely.
 */
object PiperVoiceCatalog {

    private const val BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    val voices: List<PiperVoice> = listOf(
        PiperVoice(
            id = "en_US-amy-low",
            displayName = "Amy — warm female narrator",
            language = "English (US)",
            quality = "low",
            approxSizeMb = 30,
            tarUrl = "$BASE/vits-piper-en_US-amy-low.tar.bz2",
            folderName = "vits-piper-en_US-amy-low",
            modelFileName = "en_US-amy-low.onnx"
        ),
        PiperVoice(
            id = "en_US-ryan-high",
            displayName = "Ryan — deep male narrator",
            language = "English (US)",
            quality = "high",
            approxSizeMb = 110,
            tarUrl = "$BASE/vits-piper-en_US-ryan-high.tar.bz2",
            folderName = "vits-piper-en_US-ryan-high",
            modelFileName = "en_US-ryan-high.onnx"
        ),
        PiperVoice(
            id = "en_US-lessac-medium",
            displayName = "Lessac — clear neutral voice",
            language = "English (US)",
            quality = "medium",
            approxSizeMb = 63,
            tarUrl = "$BASE/vits-piper-en_US-lessac-medium.tar.bz2",
            folderName = "vits-piper-en_US-lessac-medium",
            modelFileName = "en_US-lessac-medium.onnx"
        ),
        PiperVoice(
            id = "en_GB-alan-medium",
            displayName = "Alan — British male",
            language = "English (UK)",
            quality = "medium",
            approxSizeMb = 63,
            tarUrl = "$BASE/vits-piper-en_GB-alan-medium.tar.bz2",
            folderName = "vits-piper-en_GB-alan-medium",
            modelFileName = "en_GB-alan-medium.onnx"
        ),
        PiperVoice(
            id = "en_US-libritts_r-medium",
            displayName = "LibriTTS-R — expressive multi-speaker",
            language = "English (US)",
            quality = "medium",
            approxSizeMb = 75,
            tarUrl = "$BASE/vits-piper-en_US-libritts_r-medium.tar.bz2",
            folderName = "vits-piper-en_US-libritts_r-medium",
            modelFileName = "en_US-libritts_r-medium.onnx"
        ),
    )

    fun byVoiceId(voiceId: String): PiperVoice? = voices.find { it.voiceId == voiceId }
    fun byId(id: String): PiperVoice? = voices.find { it.id == id }
}
