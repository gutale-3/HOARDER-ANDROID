package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsManager(val application: Application) {
    private val prefs = application.getSharedPreferences("novel_hoarder_prefs", Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var currentTheme by mutableStateOf(AppTheme.CLASSIC_LIGHT)
    var readerFontSize by mutableStateOf(16)
    var readerFontFamily by mutableStateOf("serif")
    var defaultUserAgent by mutableStateOf("")

    // --- AI Configuration and State ---
    var activeAiProviderId by mutableStateOf("gemini_cloud")
    var userGeminiApiKey by mutableStateOf("")

    // --- Auto-download / Reading Queue ---
    var autoDownloadNextEnabled by mutableStateOf(true)

    // --- Reader Settings ---
    var readerTheme by mutableStateOf("light") // "light", "dark", "auto"
    var readerLineHeight by mutableStateOf(1.4f)
    var readerMargin by mutableStateOf(16) // in dp padding
    var readerLetterSpacing by mutableStateOf(0.0f)
    var readerCustomFontPath by mutableStateOf("")
    var readerCustomFontName by mutableStateOf("")
    var readerJustificationEnabled by mutableStateOf(false)
    var readerHyphenationEnabled by mutableStateOf(false)
    var readerAmbientSyncEnabled by mutableStateOf(false)

    // --- Customizable AI Prompts ---
    var glossaryPrompt by mutableStateOf("")
    var polishPrompt by mutableStateOf("")
    var recapPrompt by mutableStateOf("")

    init {
        val themeName = prefs.getString("selected_theme", AppTheme.CLASSIC_LIGHT.name)
        currentTheme = AppTheme.valueOf(themeName ?: AppTheme.CLASSIC_LIGHT.name)
        readerFontSize = prefs.getInt("reader_font_size", 18)
        readerFontFamily = prefs.getString("reader_font_family", "serif") ?: "serif"
        defaultUserAgent = prefs.getString("user_agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36") ?: ""
        activeAiProviderId = prefs.getString("active_ai_provider", "gemini_cloud") ?: "gemini_cloud"
        userGeminiApiKey = prefs.getString("gemini_api_key", "") ?: ""

        autoDownloadNextEnabled = prefs.getBoolean("auto_download_next", true)
        readerTheme = prefs.getString("reader_theme", "light") ?: "light"
        readerLineHeight = prefs.getFloat("reader_line_height", 1.4f)
        readerMargin = prefs.getInt("reader_margin", 16)
        readerLetterSpacing = prefs.getFloat("reader_letter_spacing", 0.0f)
        readerCustomFontPath = prefs.getString("reader_custom_font_path", "") ?: ""
        readerCustomFontName = prefs.getString("reader_custom_font_name", "") ?: ""
        readerJustificationEnabled = prefs.getBoolean("reader_justification_enabled", false)
        readerHyphenationEnabled = prefs.getBoolean("reader_hyphenation_enabled", false)
        readerAmbientSyncEnabled = prefs.getBoolean("reader_ambient_sync_enabled", false)

        glossaryPrompt = prefs.getString("glossary_prompt", "Analyze the following novel content and identify character names, locations, and unique terms that are poorly machine-translated or require a consistent translation glossary.") ?: "Analyze the following novel content and identify character names, locations, and unique terms that are poorly machine-translated or require a consistent translation glossary."
        polishPrompt = prefs.getString("polish_prompt", "Rewrite this machine-translated chapter to be in fluent, literary, highly readable English. Preserve the exact original plot, character actions, and meaning. Do not add any commentary or prefix/suffix notes. Only return the polished story text.") ?: "Rewrite this machine-translated chapter to be in fluent, literary, highly readable English. Preserve the exact original plot, character actions, and meaning. Do not add any commentary or prefix/suffix notes. Only return the polished story text."
        recapPrompt = prefs.getString("recap_prompt", "Provide a concise summary ('Previously on...') of the following chapter. Focus on key plot points and character actions in 2-3 sentences. Do not add metadata or conversational padding.") ?: "Provide a concise summary ('Previously on...') of the following chapter. Focus on key plot points and character actions in 2-3 sentences. Do not add metadata or conversational padding."
    }

    fun updateGeminiApiKey(key: String) {
        userGeminiApiKey = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun updateActiveAiProvider(providerId: String) {
        activeAiProviderId = providerId
        prefs.edit().putString("active_ai_provider", providerId).apply()
    }

    fun updateTheme(theme: AppTheme) {
        currentTheme = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun updateFontSize(size: Int) {
        readerFontSize = size.coerceIn(12, 36)
        prefs.edit().putInt("reader_font_size", readerFontSize).apply()
    }

    fun updateFontFamily(family: String) {
        readerFontFamily = family
        prefs.edit().putString("reader_font_family", family).apply()
    }

    fun updateReaderTheme(theme: String) {
        readerTheme = theme
        prefs.edit().putString("reader_theme", theme).apply()
    }

    fun updateReaderLineHeight(height: Float) {
        readerLineHeight = height
        prefs.edit().putFloat("reader_line_height", height).apply()
    }

    fun updateReaderMargin(margin: Int) {
        readerMargin = margin
        prefs.edit().putInt("reader_margin", margin).apply()
    }

    fun updateReaderLetterSpacing(spacing: Float) {
        readerLetterSpacing = spacing.coerceIn(-0.05f, 0.25f)
        prefs.edit().putFloat("reader_letter_spacing", readerLetterSpacing).apply()
    }

    fun updateCustomFont(path: String, name: String) {
        readerCustomFontPath = path
        readerCustomFontName = name
        prefs.edit()
            .putString("reader_custom_font_path", path)
            .putString("reader_custom_font_name", name)
            .apply()
    }

    fun updateJustificationEnabled(enabled: Boolean) {
        readerJustificationEnabled = enabled
        prefs.edit().putBoolean("reader_justification_enabled", enabled).apply()
    }

    fun updateHyphenationEnabled(enabled: Boolean) {
        readerHyphenationEnabled = enabled
        prefs.edit().putBoolean("reader_hyphenation_enabled", enabled).apply()
    }

    fun updateAmbientSyncEnabled(enabled: Boolean) {
        readerAmbientSyncEnabled = enabled
        prefs.edit().putBoolean("reader_ambient_sync_enabled", enabled).apply()
    }

    fun importCustomFont(context: Context, uri: Uri) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "CustomFont.ttf"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }

                val fontsDir = File(context.filesDir, "fonts")
                if (!fontsDir.exists()) {
                    fontsDir.mkdirs()
                }
                fontsDir.listFiles()?.forEach { it.delete() }

                val destFile = File(fontsDir, displayName)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (destFile.exists()) {
                    withContext(Dispatchers.Main) {
                        updateCustomFont(destFile.absolutePath, displayName)
                        updateFontFamily("custom")
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateAutoDownloadNextEnabled(enabled: Boolean) {
        autoDownloadNextEnabled = enabled
        prefs.edit().putBoolean("auto_download_next", enabled).apply()
    }

    fun updateGlossaryPrompt(prompt: String) {
        glossaryPrompt = prompt
        prefs.edit().putString("glossary_prompt", prompt).apply()
    }

    fun updatePolishPrompt(prompt: String) {
        polishPrompt = prompt
        prefs.edit().putString("polish_prompt", prompt).apply()
    }

    fun updateRecapPrompt(prompt: String) {
        recapPrompt = prompt
        prefs.edit().putString("recap_prompt", prompt).apply()
    }
}
