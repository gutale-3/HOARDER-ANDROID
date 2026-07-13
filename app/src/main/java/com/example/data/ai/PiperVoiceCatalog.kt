package com.example.data.ai

data class PiperVoice(
    val id: String,
    val name: String,
    val language: String,
    val accent: String,
    val gender: String,
    val description: String,
    val sizeMb: Int,
    val tarBz2Url: String,
    val folderName: String,
    val modelFilename: String, // e.g., "en_US-amy-low.onnx"
    val tokensFilename: String = "tokens.txt"
)

object PiperVoiceCatalog {
    val AMY_LOW = PiperVoice(
        id = "vits-piper-en_US-amy-low",
        name = "Amy (Low)",
        language = "English",
        accent = "American",
        gender = "Female",
        description = "Lightweight, crisp, and fast voice. Excellent for battery efficiency and rapid listening.",
        sizeMb = 28,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2",
        folderName = "vits-piper-en_US-amy-low",
        modelFilename = "en_US-amy-low.onnx"
    )

    val RYAN_MEDIUM = PiperVoice(
        id = "vits-piper-en_US-ryan-medium",
        name = "Ryan (Medium)",
        language = "English",
        accent = "American",
        gender = "Male",
        description = "Deep, clear, and exceptionally warm male voice. Rich and natural-sounding tone.",
        sizeMb = 58,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2",
        folderName = "vits-piper-en_US-ryan-medium",
        modelFilename = "en_US-ryan-medium.onnx"
    )

    val ALAN_MEDIUM = PiperVoice(
        id = "vits-piper-en_GB-alan-medium",
        name = "Alan (Medium)",
        language = "English",
        accent = "British",
        gender = "Male",
        description = "Refined, articulate British English narrator voice. Perfect for classical or literary fiction.",
        sizeMb = 58,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2",
        folderName = "vits-piper-en_GB-alan-medium",
        modelFilename = "en_GB-alan-medium.onnx"
    )

    val LIBRITTS_MEDIUM = PiperVoice(
        id = "vits-piper-en_US-libritts_r-medium",
        name = "LibriTTS (Multi-Voice)",
        language = "English",
        accent = "American",
        gender = "Multi-Speaker",
        description = "Over 900+ selectable speakers (sids) from the LibriTTS corpus. Unrivaled voice variety in one package.",
        sizeMb = 68,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2",
        folderName = "vits-piper-en_US-libritts_r-medium",
        modelFilename = "en_US-libritts_r-medium.onnx"
    )

    val ALL_VOICES = listOf(AMY_LOW, RYAN_MEDIUM, ALAN_MEDIUM, LIBRITTS_MEDIUM)

    fun getVoiceById(id: String): PiperVoice {
        return ALL_VOICES.find { it.id == id } ?: AMY_LOW
    }
}
