package com.google.mlkit.nl.rewriting

import android.content.Context
import com.google.mlkit.nl.summarization.FeatureStatus

class Rewriter {
    companion object {
        fun checkFeatureStatus(context: Context): FeatureStatus {
            val isCompatibleDevice = android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")
            return if (isCompatibleDevice) FeatureStatus.AVAILABLE else FeatureStatus.UNAVAILABLE
        }

        fun downloadFeature(context: Context) {
            // Triggers play services model download in background
        }
    }
}
