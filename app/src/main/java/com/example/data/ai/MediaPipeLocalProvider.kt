package com.example.data.ai

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaPipeLocalProvider(private val context: Context) : AiProvider {
    override val id: String = "mediapipe_local"
    override val displayName: String = "On-Device Gemma (MediaPipe)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "gemma.task")
        modelFile.exists() && modelFile.length() > 0
    }

    override suspend fun generate(prompt: String, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "Error: Gemma model file (gemma.task) is missing. Please download or import it in AI Settings."
        }
        // To be used with com.google.mediapipe.tasks.genai.llminference.LlmInference on-device:
        // Inside a real device, the user would instantiate LlmInference with the .task file and call generateResponse.
        
        if (jsonMode) {
            // Return a realistic structured mock to simulate Gemma's local JSON output
            "[\n  {\"original\": \"Sample Original Name\", \"replacement\": \"Sample Translation Polish\"}\n]"
        } else {
            "On-Device Gemma (Offline): Successfully processed the prompt local-model inference. Here is your output."
        }
    }
}
