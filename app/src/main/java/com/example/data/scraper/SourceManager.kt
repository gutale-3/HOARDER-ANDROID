package com.example.data.scraper

import com.example.util.TomatoScraper

object SourceManager {
    private val sources = listOf<NovelSource>(
        TomatoScraper
    )

    /**
     * Resolves the appropriate NovelSource adapter based on the input URL.
     * Defaults to TomatoScraper if no matches are found, maintaining full backwards compatibility.
     */
    fun getSourceForUrl(url: String): NovelSource {
        val lowerUrl = url.lowercase()
        return sources.firstOrNull { source ->
            lowerUrl.contains(source.sourceName.lowercase()) || 
            (source == TomatoScraper && (lowerUrl.contains("tomatoy") || lowerUrl.contains("tomato")))
        } ?: TomatoScraper
    }
}
