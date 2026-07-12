package com.example.data.ai

interface AiProvider {
    val id: String
    val displayName: String
    suspend fun isAvailable(): Boolean
    suspend fun generate(prompt: String, jsonMode: Boolean = false): String
}
