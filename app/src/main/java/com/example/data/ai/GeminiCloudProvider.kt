package com.example.data.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

interface GeminiApi {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiCloudProvider(private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY }) : AiProvider {
    override val id: String = "gemini_cloud"
    override val displayName: String = "Cloud Gemini"

    private val api: GeminiApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        retrofit.create(GeminiApi::class.java)
    }

    override suspend fun isAvailable(): Boolean {
        val key = apiKeyProvider()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    override suspend fun generate(prompt: String, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()
        if (!isAvailable()) {
            return@withContext "Error: Gemini API Key is missing. Please enter your GEMINI_API_KEY in the Secrets panel or AI Settings."
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = if (jsonMode) GeminiGenerationConfig(responseMimeType = "application/json") else null
        )

        try {
            val response = api.generateContent(key, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            text ?: "Error: Empty response from Gemini"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
