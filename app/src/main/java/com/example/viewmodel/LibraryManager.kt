package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.GlossaryEntity
import com.example.data.local.BookmarkEntity
import com.example.data.repository.NovelRepository
import com.example.data.scraper.SourceManager
import com.example.util.NovelCompiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryManager(
    private val application: Application,
    private val repository: NovelRepository,
    private val scrapingManager: ScrapingManager,
    private val onClearSelection: () -> Unit
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- Book Deletion and Export Helpers ---
    fun deleteBook(bookId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Delete local folders if any exist
                val context = application.applicationContext
                val folder = File(context.filesDir, bookId)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
                repository.deleteBook(bookId)
                scrapingManager.addLog("Deleted novel database record and associated storage files: $bookId")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun compileFormat(book: BookEntity, format: String, onFinished: (Boolean, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val context = application.applicationContext
            val chapters = repository.getChapters(book.id)
            val outputFolder = File(context.filesDir, book.id)
            if (!outputFolder.exists()) outputFolder.mkdirs()

            if (format == "EPUB") {
                val file = File(outputFolder, "${book.id}.epub")
                val ok = NovelCompiler.compileEpub(context, book, chapters, file)
                withContext(Dispatchers.Main) {
                    onFinished(ok, if (ok) file.absolutePath else "")
                }
            } else if (format == "PDF") {
                val file = File(outputFolder, "${book.id}.pdf")
                val ok = NovelCompiler.compilePdf(context, book, chapters, file)
                withContext(Dispatchers.Main) {
                    onFinished(ok, if (ok) file.absolutePath else "")
                }
            }
        }
    }

    // --- Bulk Operations ---
    fun bulkDeleteBooks(bookIds: Set<String>) {
        coroutineScope.launch(Dispatchers.IO) {
            for (id in bookIds) {
                repository.deleteBook(id)
            }
            withContext(Dispatchers.Main) {
                onClearSelection()
            }
        }
    }

    fun bulkReScrapeBooks(bookIds: Set<String>, onResult: (String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            var checked = 0
            var added = 0
            for (id in bookIds) {
                val book = repository.getBook(id) ?: continue
                try {
                    val scraper = SourceManager.getSourceForUrl(book.url)
                    val webView = withContext(Dispatchers.Main) {
                        WebView(application.applicationContext).apply {
                            settings.javaScriptEnabled = true
                        }
                    }
                    val urls = scraper.scrapeChapterList(webView, book.url)
                    if (urls.isNotEmpty()) {
                        val currentCount = repository.getChapterCount(book.id)
                        val diff = urls.size - currentCount
                        if (diff > 0) {
                            added += diff
                        }
                    }
                    checked++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                onClearSelection()
                onResult("Checked $checked novels. Found $added new chapters to download!")
            }
        }
    }

    // --- Library Backup / Restore ---
    fun backupLibrary(context: Context, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val books = repository.getAllBooks()
                val allChapters = mutableListOf<ChapterEntity>()
                val allGlossaries = repository.getAllGlossaries()
                val allBookmarks = repository.getAllBookmarks()
                
                for (book in books) {
                    allChapters.addAll(repository.getChapters(book.id))
                }
                
                val backupObj = org.json.JSONObject()
                
                // Books
                val booksArray = org.json.JSONArray()
                for (b in books) {
                    val j = org.json.JSONObject().apply {
                        put("id", b.id)
                        put("title", b.title)
                        put("author", b.author)
                        put("coverUrl", b.coverUrl)
                        put("coverLocalPath", b.coverLocalPath)
                        put("totalChapters", b.totalChapters)
                        put("url", b.url)
                        put("lastReadChapterId", b.lastReadChapterId)
                        put("synopsis", b.synopsis)
                    }
                    booksArray.put(j)
                }
                backupObj.put("books", booksArray)
                
                // Chapters
                val chaptersArray = org.json.JSONArray()
                for (c in allChapters) {
                    val j = org.json.JSONObject().apply {
                        put("id", c.id)
                        put("bookId", c.bookId)
                        put("chapterId", c.chapterId)
                        put("chapterNumber", c.chapterNumber)
                        put("title", c.title)
                        put("url", c.url)
                        put("content", c.content)
                        put("hash", c.hash)
                        put("isRead", c.isRead)
                    }
                    chaptersArray.put(j)
                }
                backupObj.put("chapters", chaptersArray)
                
                // Glossaries
                val glossariesArray = org.json.JSONArray()
                for (g in allGlossaries) {
                    val j = org.json.JSONObject().apply {
                        put("id", g.id)
                        put("bookId", g.bookId)
                        put("originalText", g.originalText)
                        put("replacementText", g.replacementText)
                    }
                    glossariesArray.put(j)
                }
                backupObj.put("glossaries", glossariesArray)
                
                // Bookmarks
                val bookmarksArray = org.json.JSONArray()
                for (b in allBookmarks) {
                    val j = org.json.JSONObject().apply {
                        put("id", b.id)
                        put("bookId", b.bookId)
                        put("chapterId", b.chapterId)
                        put("paragraphIndex", b.paragraphIndex)
                        put("text", b.text)
                        put("note", b.note)
                        put("timestamp", b.timestamp)
                    }
                    bookmarksArray.put(j)
                }
                backupObj.put("bookmarks", bookmarksArray)
                
                val backupFile = java.io.File(context.cacheDir, "novel_hoarder_library_backup.json")
                backupFile.writeText(backupObj.toString())
                
                withContext(Dispatchers.Main) {
                    onResult(true, backupFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Failed to generate backup JSON")
                }
            }
        }
    }

    fun restoreLibrary(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Could not open input stream")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val backupObj = org.json.JSONObject(jsonString)
                
                // Books
                val booksArray = backupObj.optJSONArray("books")
                if (booksArray != null) {
                    val list = mutableListOf<BookEntity>()
                    for (i in 0 until booksArray.length()) {
                        val j = booksArray.getJSONObject(i)
                        list.add(
                            BookEntity(
                                id = j.getString("id"),
                                title = j.getString("title"),
                                author = j.getString("author"),
                                coverUrl = j.getString("coverUrl"),
                                coverLocalPath = j.optString("coverLocalPath", null),
                                totalChapters = j.getInt("totalChapters"),
                                url = j.getString("url"),
                                lastReadChapterId = j.optString("lastReadChapterId", null),
                                synopsis = j.optString("synopsis", "")
                            )
                        )
                    }
                    repository.insertBooks(list)
                }
                
                // Chapters
                val chaptersArray = backupObj.optJSONArray("chapters")
                if (chaptersArray != null) {
                    val list = mutableListOf<ChapterEntity>()
                    for (i in 0 until chaptersArray.length()) {
                        val j = chaptersArray.getJSONObject(i)
                        list.add(
                            ChapterEntity(
                                id = j.getString("id"),
                                bookId = j.getString("bookId"),
                                chapterId = j.getString("chapterId"),
                                chapterNumber = j.getInt("chapterNumber"),
                                title = j.getString("title"),
                                url = j.getString("url"),
                                content = j.getString("content"),
                                hash = j.optString("hash", ""),
                                isRead = j.optBoolean("isRead", false)
                            )
                        )
                    }
                    repository.insertChapters(list)
                }
                
                // Glossaries
                val glossariesArray = backupObj.optJSONArray("glossaries")
                if (glossariesArray != null) {
                    val list = mutableListOf<GlossaryEntity>()
                    for (i in 0 until glossariesArray.length()) {
                        val j = glossariesArray.getJSONObject(i)
                        list.add(
                            GlossaryEntity(
                                id = j.getInt("id"),
                                bookId = j.getString("bookId"),
                                originalText = j.getString("originalText"),
                                replacementText = j.getString("replacementText")
                            )
                        )
                    }
                    repository.insertGlossaries(list)
                }
                
                // Bookmarks
                val bookmarksArray = backupObj.optJSONArray("bookmarks")
                if (bookmarksArray != null) {
                    val list = mutableListOf<BookmarkEntity>()
                    for (i in 0 until bookmarksArray.length()) {
                        val j = bookmarksArray.getJSONObject(i)
                        list.add(
                            BookmarkEntity(
                                id = j.getString("id"),
                                bookId = j.getString("bookId"),
                                chapterId = j.getString("chapterId"),
                                paragraphIndex = j.getInt("paragraphIndex"),
                                text = j.getString("text"),
                                note = j.optString("note", ""),
                                timestamp = j.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                    repository.insertBookmarks(list)
                }
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Library restored successfully!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to restore: ${e.message}")
                }
            }
        }
    }

    // --- Import Local File ---
    fun importLocalFile(context: Context, uri: Uri, isEpub: Boolean, customUrl: String? = null, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val result = if (isEpub) {
                com.example.util.EpubImporter.importEpub(context, uri)
            } else {
                com.example.util.EpubImporter.importTxt(context, uri)
            }
            
            if (result != null) {
                val finalUrl = if (!customUrl.isNullOrBlank()) customUrl.trim() else result.first.url
                val updatedBook = result.first.copy(url = finalUrl)
                repository.insertBook(updatedBook)
                repository.insertChapters(result.second)
                withContext(Dispatchers.Main) {
                    onResult(true, "Imported \"${result.first.title}\" with ${result.second.size} chapters!")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to parse local book file. Ensure the format is valid.")
                }
            }
        }
    }

    // --- Update Book Details (Cover, Author, Title, URL) ---
    fun updateBookDetails(
        bookId: String,
        newTitle: String,
        newAuthor: String,
        newUrl: String,
        newCoverUrl: String?,
        newCoverLocalPath: String?,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            val book = repository.getBook(bookId)
            if (book != null) {
                val updatedBook = book.copy(
                    title = newTitle.trim(),
                    author = newAuthor.trim(),
                    url = if (newUrl.isNotBlank()) newUrl.trim() else book.url,
                    coverUrl = newCoverUrl?.trim()?.ifEmpty { null },
                    coverLocalPath = newCoverLocalPath?.trim()?.ifEmpty { null },
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertBook(updatedBook)
                withContext(Dispatchers.Main) {
                    onResult(true, "Novel details updated successfully!")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Novel not found.")
                }
            }
        }
    }

    // --- Background Chapter Updates Checker ---
    fun scheduleChapterUpdatesCheck() {
        val context = application.applicationContext
        try {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.background.ChapterUpdateWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            ).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "chapter_updates_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
