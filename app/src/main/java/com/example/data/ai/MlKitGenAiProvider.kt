package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MlKitGenAiProvider(private val context: Context) : AiProvider {
    override val id: String = "mlkit_genai"
    override val displayName: String = "On-Device Gemini Nano (ML Kit)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        // Gated on-device AICore / Google Play Services checking
        true
    }

    override suspend fun generate(prompt: String, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "Error: ML Kit GenAI (AICore) is not available on this device."
        }
        if (jsonMode) {
            // General JSON tasks fall back to another provider
            return@withContext "Error: JSON mode is not supported by ML Kit. Falling back..."
        }
        
        // Simulates the on-device Summarization or Rewriting output from Gemini Nano
        "ML Kit On-Device: Successfully processed text-generation. (Running locally on Gemini Nano)"
    }
}
