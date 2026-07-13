package com.example.util

import android.webkit.CookieManager
import android.webkit.WebView
import com.example.data.local.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class CloudflareException(message: String) : IOException(message)

object TomatoScraper {

    private val AD_PATTERNS = listOf(
        Pattern.compile("Read on TomatoMTL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Translator:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Editor:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Watermark", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Visit [^\\n]* for the best novel reading experience.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Read (this|the latest) (chapter )?(at|on) [^\\n]*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("This chapter (is updated by|upload first at) [^\\n]*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Follow current novels? on [^\\n]*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("If you (find|found) any errors.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Please support the (author|translator).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("©.*All rights reserved.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Click to claim the \"[^\"]*\" buff!", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Click to claim [^\\n]*buff!", Pattern.CASE_INSENSITIVE)
    )

    fun parseBookId(url: String): String? {
        val cleanUrl = url.trim().replace("https://", "").replace("http://", "")
        val segments = cleanUrl.split("/").filter { it.isNotEmpty() }
        val bookIndex = segments.indexOf("book")
        if (bookIndex != -1 && bookIndex + 1 < segments.size) {
            return segments[bookIndex + 1]
        }
        return null
    }

    fun parseChapterId(url: String): String? {
        val cleanUrl = url.trim().replace("https://", "").replace("http://", "")
        val segments = cleanUrl.split("/").filter { it.isNotEmpty() }
        val bookIndex = segments.indexOf("book")
        if (bookIndex != -1 && bookIndex + 2 < segments.size) {
            return segments[bookIndex + 2]
        }
        return null
    }

    fun sanitizeText(rawText: String, aggressive: Boolean): String {
        var clean = rawText
        for (pattern in AD_PATTERNS) {
            clean = pattern.matcher(clean).replaceAll("")
        }
        if (aggressive) {
            // Remove annotations in square brackets up to 80 chars
            clean = clean.replace(Regex("\\[[^\\]\\n]{0,80}\\]"), "")
        }
        // Deduplicate newlines
        clean = clean.replace(Regex("\\n{3,}"), "\n\n")
        return clean.trim()
    }

    private suspend fun WebView.evaluateJs(script: String): String = suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result ->
            if (continuation.isActive) {
                continuation.resume(result ?: "null")
            }
        }
    }

    private suspend fun loadUrlAndWait(
        webView: WebView,
        url: String,
        checkReadyJs: String,
        maxWaitSeconds: Int = 30,
        shouldSkip: () -> Boolean = { false }
    ): String {
        return withContext(Dispatchers.Main) {
            try {
                webView.evaluateJs("window.__oldPage = true;")
            } catch (e: Exception) {
                // ignore
            }
            webView.loadUrl(url)
            var attempts = 0
            val totalAttempts = maxWaitSeconds * 2 // check every 500ms
            var result = "null"
            while (attempts < totalAttempts) {
                delay(500)
                if (!coroutineContext.isActive || shouldSkip()) {
                    break
                }

                // Check if WebView is still showing the old page
                val isOld = webView.evaluateJs("window.__oldPage === true")
                if (isOld == "true" || isOld == "\"true\"") {
                    attempts++
                    continue
                }

                // 1. Detect Cloudflare via page Title
                val title = webView.title ?: ""
                val lowerTitle = title.lowercase()
                if (lowerTitle.contains("just a moment") || 
                    lowerTitle.contains("please wait... | cloudflare") || 
                    lowerTitle.contains("attention required! | cloudflare") ||
                    lowerTitle.contains("cloudflare-nginx") ||
                    lowerTitle.contains("ddos protection")
                ) {
                    throw CloudflareException("Cloudflare challenge page encountered in Title")
                }

                // 2. Detect Cloudflare via body text
                val checkLower = webView.evaluateJs("""
                    (() => {
                        const lower = document.body?.innerText?.toLowerCase() || "";
                        return lower.includes("verify you are human") || 
                               lower.includes("cf-challenge") || 
                               lower.includes("checking your browser") ||
                               lower.includes("are you a robot");
                    })()
                """.trimIndent())
                if (checkLower == "true") {
                    throw CloudflareException("Cloudflare challenge page detected in Body")
                }

                // 3. Evaluate page readiness
                val readyRes = webView.evaluateJs(checkReadyJs)
                if (readyRes != "null" && readyRes != "\"null\"" && readyRes.trim().isNotEmpty()) {
                    result = readyRes
                    if (readyRes.startsWith("{") || readyRes.startsWith("[")) {
                        try {
                            if (readyRes.startsWith("{")) {
                                val json = JSONObject(readyRes)
                                if (json.optBoolean("ready", false)) {
                                    break
                                }
                            } else {
                                val jsonArray = JSONArray(readyRes)
                                if (jsonArray.length() > 0) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // ignore json parse error
                        }
                    } else if (readyRes != "[]" && readyRes != "\"[]\"") {
                        break
                    }
                }
                attempts++
            }
            result
        }
    }

    suspend fun scrapeBookInfo(webView: WebView, url: String): BookEntity {
        val checkJs = """
            (() => {
                const q = (s) => { const e = document.querySelector(s); return e ? e.innerText.trim() : ""; };
                const attr = (s, a) => { const e = document.querySelector(s); return e ? e.getAttribute(a) || "" : ""; };
                
                // Resilient synopsis extraction
                let desc = q('.book-description') || q('p.description') || q('#synopsis') || q('.synopsis') || q('.description');
                
                // Resilient author extraction
                let author = "";
                document.querySelectorAll('.book-meta-item, .meta-item, p').forEach(e => {
                    const t = (e.innerText || '').trim();
                    if (/^Author:/i.test(t) && !author) {
                        author = t.replace(/^Author:\s*/i, '').split('\n')[0].trim();
                    }
                });
                
                // Resilient cover extraction
                let cover = attr('meta[property="og:image"]', 'content') || 
                            attr('img.book-cover', 'src') || 
                            attr('.book-cover img', 'src') || 
                            attr('img[src*="cover"]', 'src') || "";
                
                // Resilient title extraction
                let title = attr('meta[property="og:title"]', 'content') || q('h1') || q('.book-title') || q('.book-info h1') || "";
                
                return {
                    ready: title.length > 0,
                    title: title,
                    author: author || "Unknown",
                    synopsis: desc || "No synopsis available.",
                    cover: cover
                };
            })()
        """.trimIndent()

        val jsonStr = loadUrlAndWait(webView, url, checkJs, 25)
        if (jsonStr == "null" || jsonStr == "\"null\"" || jsonStr.trim().isEmpty()) {
            throw IOException("Failed to load and parse book details")
        }

        val json = JSONObject(jsonStr)
        val bookId = parseBookId(url) ?: "unknown_book"
        val title = json.optString("title", "").trim().ifEmpty { "Tomato Novel $bookId" }
        val author = json.optString("author", "Unknown").trim()
        val synopsis = json.optString("synopsis", "No synopsis available.").trim()
        var coverUrl = json.optString("cover", "").trim()

        if (coverUrl.isNotEmpty() && !coverUrl.contains("wsrv.nl") && coverUrl.startsWith("http")) {
            coverUrl = "https://images.weserv.nl/?url=${coverUrl}&output=jpg&w=400"
        }

        return BookEntity(
            id = bookId,
            url = url,
            title = title,
            author = author,
            synopsis = synopsis,
            coverUrl = if (coverUrl.isEmpty()) null else coverUrl,
            coverLocalPath = null,
            lastReadChapterId = null,
            totalChapters = 0,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun scrapeChapterList(webView: WebView, bookUrl: String): List<String> {
        val checkJs = """
            (() => {
                const links = document.querySelectorAll('a.chapter-link, .chapter-container a, .chapter-list a');
                return {
                    ready: links.length > 0,
                    count: links.length
                };
            })()
        """.trimIndent()

        loadUrlAndWait(webView, bookUrl, checkJs, 30)

        val bookId = parseBookId(bookUrl) ?: return emptyList()
        val extractJs = """
            (() => {
                const bookId = "$bookId";
                const re = new RegExp('^/book/' + bookId + '/\\d+/?${'$'}');
                const JUNK = ['start reading', 'continue reading', 'read now', 'latest'];
                const seen = [];
                
                const collect = (elements) => {
                    elements.forEach(a => {
                        const h = a.getAttribute('href');
                        if (!h) return;
                        const txt = (a.textContent || '').trim().toLowerCase();
                        if (JUNK.includes(txt)) return;
                        try {
                            const u = new URL(h, location.href);
                            if (u.hostname !== location.hostname) return;
                            if (!re.test(u.pathname)) return;
                            const norm = u.origin + u.pathname.replace(/\/$/, '');
                            if (!seen.includes(norm)) { seen.push(norm); }
                        } catch(e) {}
                    });
                };

                const containers = document.querySelectorAll('.chapter-container');
                if (containers.length) {
                    containers.forEach(c => collect(c.querySelectorAll('a')));
                }
                if (!seen.length) {
                    collect(document.querySelectorAll('a'));
                }
                return seen.slice(0, 10000);
            })()
        """.trimIndent()

        val listJson = withContext(Dispatchers.Main) { webView.evaluateJs(extractJs) }
        val urls = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(listJson)
            for (i in 0 until jsonArray.length()) {
                urls.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return urls
    }

    suspend fun scrapeChapterContent(
        webView: WebView,
        chapterUrl: String,
        shouldSkip: () -> Boolean = { false }
    ): Pair<String, String> {
        val checkJs = """
            (() => {
                const art = document.querySelector('#chapter_content');
                const wrap = document.querySelector('#noidung');
                const ph = art ? art.querySelectorAll('.placeholder').length : 0;
                const body = art ? art.innerText.trim() : (wrap ? wrap.innerText.trim() : '');
                const title = document.querySelector('h1, h2, .chapter-title, .chapter-heading')?.innerText?.trim() || 'Chapter';
                
                return {
                    ready: ph === 0 && body.length > 50,
                    title: title,
                    body: body
                };
            })()
        """.trimIndent()

        val jsonStr = loadUrlAndWait(webView, chapterUrl, checkJs, 35, shouldSkip)
        if (jsonStr == "null" || jsonStr == "\"null\"" || jsonStr.trim().isEmpty()) {
            throw IOException("Failed to scrape chapter text content")
        }

        val json = JSONObject(jsonStr)
        val title = json.optString("title", "Chapter").trim()
        val body = json.optString("body", "").trim()

        if (body.isEmpty()) {
            throw IOException("No chapter text content found on page")
        }

        return Pair(title, body)
    }
}
