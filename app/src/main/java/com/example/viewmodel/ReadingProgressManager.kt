package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.BookmarkEntity
import com.example.data.repository.NovelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadingProgressManager(
    private val application: Application,
    private val repository: NovelRepository,
    private val tts: TtsPlaybackManager,
    private val coroutineScope: CoroutineScope
) {
    private val prefs = application.getSharedPreferences("novel_hoarder_prefs", Context.MODE_PRIVATE)

    // --- State Properties delegated to tts ---
    var hasResumableSession: Boolean
        get() = tts.hasResumableSession
        set(value) { tts.hasResumableSession = value }

    var resumeBookId: String
        get() = tts.resumeBookId
        set(value) { tts.resumeBookId = value }

    var resumeChapterId: String
        get() = tts.resumeChapterId
        set(value) { tts.resumeChapterId = value }

    var resumeParagraph: Int
        get() = tts.resumeParagraph
        set(value) { tts.resumeParagraph = value }

    var resumeBookName: String
        get() = tts.resumeBookName
        set(value) { tts.resumeBookName = value }

    var resumeChapterTitle: String
        get() = tts.resumeChapterTitle
        set(value) { tts.resumeChapterTitle = value }

    init {
        loadResumableTtsSession()
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                checkAndAutoArchiveChapters()
                delay(5 * 60 * 1000) // Run every 5 minutes
            }
        }
    }

    private fun addLog(msg: String) {
        tts.addLog(msg)
    }

    fun updateChaptersReadStatus(chapterIds: List<String>, isRead: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.updateChaptersReadStatus(chapterIds, isRead)
        }
    }

    fun updateChaptersArchiveStatus(chapterIds: List<String>, isArchived: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.updateChaptersArchiveStatus(chapterIds, isArchived)
            addLog("Successfully ${if (isArchived) "archived" else "unarchived"} ${chapterIds.size} chapters.")
        }
    }

    fun updateBookAutoArchiveHours(bookId: String, hours: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            val book = repository.getBook(bookId)
            if (book != null) {
                val updatedBook = book.copy(autoArchiveHours = hours)
                repository.updateBook(updatedBook)
                addLog("Updated auto-archive setting for ${book.title} to: ${if (hours == 0) "Never" else "$hours hours"}")
                checkAndAutoArchiveChapters()
            }
        }
    }

    fun getArchivedChaptersFlow(bookId: String): Flow<List<ChapterEntity>> {
        return repository.getArchivedChaptersFlow(bookId)
    }

    fun checkAndAutoArchiveChapters() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val books = repository.getAllBooks()
                books.forEach { book ->
                    val hours = book.autoArchiveHours
                    if (hours > 0) {
                        val chapters = repository.getChapters(book.id)
                        val now = System.currentTimeMillis()
                        val chaptersToArchive = chapters.filter { ch ->
                            ch.isRead && !ch.isArchived && ch.readAt != null && (now - ch.readAt) >= (hours.toLong() * 60 * 60 * 1000)
                        }
                        if (chaptersToArchive.isNotEmpty()) {
                            val ids = chaptersToArchive.map { it.id }
                            repository.updateChaptersArchiveStatus(ids, true)
                            addLog("Auto-archived ${ids.size} read chapters for book: ${book.title}")
                        }
                    }
                }
            } catch (e: Exception) {
                addLog("Error in auto-archiving: ${e.message}")
            }
        }
    }

    fun deleteChapters(bookId: String, chapterIds: List<String>, onComplete: (String) -> Unit = {}) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Delete actual chapters from Room
                repository.deleteChapters(chapterIds)
                
                // Recalculate total chapters for this book
                val newCount = repository.getChapterCount(bookId)
                val book = repository.getBook(bookId)
                if (book != null) {
                    val updatedBook = book.copy(
                        totalChapters = newCount,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateBook(updatedBook)
                }
                
                // Clean up polished chapters and recaps
                chapterIds.forEach { chId ->
                    repository.deletePolishedChapter(chId)
                    repository.deleteChapterRecap(chId)
                }
                
                withContext(Dispatchers.Main) {
                    addLog("Successfully deleted ${chapterIds.size} chapters from Book $bookId")
                    onComplete("Deleted ${chapterIds.size} chapters successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("Error deleting chapters: ${e.message}")
                    onComplete("Error deleting chapters: ${e.message}")
                }
            }
        }
    }

    fun startTtsForBook(book: BookEntity) {
        coroutineScope.launch(Dispatchers.IO) {
            val chapters = repository.getChapters(book.id)
            if (chapters.isEmpty()) {
                withContext(Dispatchers.Main) {
                    addLog("No chapters found for book: ${book.title}")
                }
                return@launch
            }
            val activeChapter = if (!book.lastReadChapterId.isNullOrEmpty()) {
                chapters.find { it.id == book.lastReadChapterId } ?: chapters.first()
            } else {
                chapters.first()
            }
            withContext(Dispatchers.Main) {
                tts.speak(activeChapter.content, book, activeChapter)
            }
        }
    }

    fun saveTtsProgress() = tts.saveTtsProgress()
    fun clearTtsProgress() = tts.clearTtsProgress()
    fun loadResumableTtsSession() = tts.loadResumableTtsSession()
    fun resumeLastSession() = tts.resumeLastSession()
    fun seekToParagraph(index: Int) = tts.seekToParagraph(index)
    fun skipParagraph(delta: Int) = tts.skipParagraph(delta)

    fun getSavedParagraphIndex(bookId: String, chapterId: String): Int {
        return prefs.getInt("progress_para_${bookId}_${chapterId}", 0)
    }

    fun saveReadingProgress(bookId: String, chapterId: String, paragraphIndex: Int) {
        prefs.edit()
            .putInt("progress_para_${bookId}_${chapterId}", paragraphIndex)
            .putString("progress_chapter_${bookId}", chapterId)
            .apply()
    }

    fun autoSaveProgressAndBookmark(
        bookId: String,
        chapterId: String,
        paragraphIndex: Int,
        paragraphText: String
    ) {
        saveReadingProgress(bookId, chapterId, paragraphIndex)

        if (paragraphText.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val bookmarkId = "${bookId}_${chapterId}_${paragraphIndex}"
                val bookmark = BookmarkEntity(
                    id = bookmarkId,
                    bookId = bookId,
                    chapterId = chapterId,
                    paragraphIndex = paragraphIndex,
                    text = paragraphText,
                    note = "Auto-saved Progress",
                    timestamp = System.currentTimeMillis()
                )
                repository.insertBookmark(bookmark)
            }
        }
    }
}
