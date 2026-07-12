package com.example.data.ai

import android.content.Context

class AiProviderRegistry(private val context: Context) {
    val cloudProvider = GeminiCloudProvider()
    val localProvider = MediaPipeLocalProvider(context)
    val mlKitProvider = MlKitGenAiProvider(context)

    val providers = listOf(cloudProvider, localProvider, mlKitProvider)

    fun getProvider(id: String): AiProvider {
        return providers.find { it.id == id } ?: cloudProvider
    }

    suspend fun getActiveProviderForTask(preferredId: String, requiresJson: Boolean): AiProvider {
        val preferred = getProvider(preferredId)
        
        // 1. Check if preferred is available
        if (preferred.isAvailable()) {
            // ML Kit does not support JSON mode
            if (requiresJson && preferred.id == "mlkit_genai") {
                // Fallback to local or cloud
                if (localProvider.isAvailable()) return localProvider
                return cloudProvider
            }
            return preferred
        }

        // 2. Fallbacks
        if (localProvider.isAvailable()) {
            return localProvider
        }
        
        // Default to cloud
        return cloudProvider
    }
}
