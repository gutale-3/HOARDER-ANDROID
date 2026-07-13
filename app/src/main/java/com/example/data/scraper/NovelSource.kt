package com.example.data.scraper

import android.webkit.WebView
import com.example.data.local.BookEntity

interface NovelSource {
    val sourceName: String
    
    /**
     * Parse the unique book ID from the URL.
     */
    fun parseBookId(url: String): String?

    /**
     * Parse the unique chapter ID from the URL.
     */
    fun parseChapterId(url: String): String?

    /**
     * Scrape the book information from the page using WebView.
     */
    suspend fun scrapeBookInfo(webView: WebView, url: String): BookEntity

    /**
     * Scrape the full list of chapter URLs for a book.
     */
    suspend fun scrapeChapterList(webView: WebView, bookUrl: String): List<String>

    /**
     * Scrape a specific chapter's title and raw text content.
     */
    suspend fun scrapeChapterContent(
        webView: WebView,
        chapterUrl: String,
        shouldSkip: () -> Boolean
    ): Pair<String, String>
}
