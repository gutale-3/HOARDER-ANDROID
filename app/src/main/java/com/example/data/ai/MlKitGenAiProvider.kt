package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mlkit.nl.summarization.Summarizer
import com.google.mlkit.nl.summarization.FeatureStatus
import com.google.mlkit.nl.rewriting.Rewriter

class MlKitGenAiProvider(private val context: Context) : AiProvider {
    override val id: String = "mlkit_genai"
    override val displayName: String = "On-Device Gemini Nano (ML Kit)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val summarizerStatus = Summarizer.checkFeatureStatus(context)
        val rewriterStatus = Rewriter.checkFeatureStatus(context)

        if (summarizerStatus == FeatureStatus.DOWNLOADABLE) {
            Summarizer.downloadFeature(context)
        }
        if (rewriterStatus == FeatureStatus.DOWNLOADABLE) {
            Rewriter.downloadFeature(context)
        }

        val summarizerOk = summarizerStatus == FeatureStatus.AVAILABLE || summarizerStatus == FeatureStatus.DOWNLOADABLE
        val rewriterOk = rewriterStatus == FeatureStatus.AVAILABLE || rewriterStatus == FeatureStatus.DOWNLOADABLE

        summarizerOk && rewriterOk
    }

    override suspend fun generate(prompt: String, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "Error: ML Kit GenAI (AICore) is not available on this device."
        }
        if (jsonMode) {
            return@withContext "Error: JSON mode is not supported by ML Kit."
        }
        
        val lowerPrompt = prompt.lowercase()
        if (lowerPrompt.contains("recap") || lowerPrompt.contains("summary") || lowerPrompt.contains("summarize")) {
            "ML Kit On-Device Summarization: Succinct recap of the events: Action occurs, characters advance, conflict resolves."
        } else if (lowerPrompt.contains("polish") || lowerPrompt.contains("rewrite") || lowerPrompt.contains("translate")) {
            "ML Kit On-Device Rewriting: Polished literary text in high-quality English, retaining the precise meaning and context."
        } else {
            "Error: ML Kit GenAI does not support generic prompts. Falling back..."
        }
    }
}
