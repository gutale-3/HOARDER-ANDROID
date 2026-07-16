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
    val tokensFilename: String = "tokens.txt",
    val isKokoro: Boolean = false,
    val kokoroSpeakerId: Int = 0,
    val voicesFilename: String = "voices.bin"
)

object PiperVoiceCatalog {
    val KOKORO_BELLA = PiperVoice(
        id = "kokoro-en-bella",
        name = "Bella",
        language = "English",
        accent = "American",
        gender = "Female",
        description = "Kokoro high-quality female voice (Bella). Extremely clear and natural.",
        sizeMb = 82,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
        folderName = "kokoro-en-v0_19",
        modelFilename = "model.onnx",
        isKokoro = true,
        kokoroSpeakerId = 0
    )

    val KOKORO_SARAH = PiperVoice(
        id = "kokoro-en-sarah",
        name = "Sarah",
        language = "English",
        accent = "American",
        gender = "Female",
        description = "Kokoro high-quality female voice (Sarah). Smooth and melodic.",
        sizeMb = 82,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
        folderName = "kokoro-en-v0_19",
        modelFilename = "model.onnx",
        isKokoro = true,
        kokoroSpeakerId = 1
    )

    val KOKORO_ADAM = PiperVoice(
        id = "kokoro-en-adam",
        name = "Adam",
        language = "English",
        accent = "American",
        gender = "Male",
        description = "Kokoro high-quality male voice (Adam). Deep, articulate, and professional.",
        sizeMb = 82,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
        folderName = "kokoro-en-v0_19",
        modelFilename = "model.onnx",
        isKokoro = true,
        kokoroSpeakerId = 2
    )

    val KOKORO_MICHAEL = PiperVoice(
        id = "kokoro-en-michael",
        name = "Michael",
        language = "English",
        accent = "American",
        gender = "Male",
        description = "Kokoro high-quality male voice (Michael). Warm, inviting, and rich narrator.",
        sizeMb = 82,
        tarBz2Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
        folderName = "kokoro-en-v0_19",
        modelFilename = "model.onnx",
        isKokoro = true,
        kokoroSpeakerId = 3
    )

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

    val ALL_VOICES = listOf(
        KOKORO_BELLA, KOKORO_SARAH, KOKORO_ADAM, KOKORO_MICHAEL,
        AMY_LOW, RYAN_MEDIUM, ALAN_MEDIUM, LIBRITTS_MEDIUM
    )

    fun getVoiceById(id: String): PiperVoice {
        return ALL_VOICES.find { it.id == id } ?: AMY_LOW
    }
}
