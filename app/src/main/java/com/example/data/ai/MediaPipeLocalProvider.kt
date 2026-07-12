package com.example.data.ai

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class MediaPipeLocalProvider(private val context: Context) : AiProvider {
    override val id: String = "mediapipe_local"
    override val displayName: String = "On-Device Gemma (MediaPipe)"

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "gemma.task")
        modelFile.exists() && modelFile.length() > 0
    }

    @Synchronized
    private fun getOrInitLlmInference(modelPath: String): LlmInference {
        if (llmInference == null || loadedModelPath != modelPath) {
            llmInference?.close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
        }
        return llmInference!!
    }

    override suspend fun generate(prompt: String, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "Error: Gemma model file (gemma.task) is missing. Please download or import it in AI Settings."
        }
        
        try {
            val modelFile = File(context.filesDir, "gemma.task")
            val inference = getOrInitLlmInference(modelFile.absolutePath)
            
            val finalPrompt = if (jsonMode) {
                "$prompt\nIMPORTANT: Return ONLY a valid JSON array containing objects with 'original' and 'replacement' properties. Do not include markdown code blocks, backticks, or other conversation filler."
            } else {
                prompt
            }
            
            val response = inference.generateResponse(finalPrompt)
            response ?: "Error: Empty response from on-device model"
        } catch (e: Exception) {
            "Error running on-device inference: ${e.message}"
        }
    }

    @Synchronized
    fun reset() {
        llmInference?.close()
        llmInference = null
        loadedModelPath = null
    }
}
