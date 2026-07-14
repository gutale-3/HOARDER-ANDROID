package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow
import java.io.File

class NovelRepository(private val bookDao: BookDao) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooksFlow()

    suspend fun getAllBooks(): List<BookEntity> = bookDao.getAllBooks()

    suspend fun getBook(id: String): BookEntity? = bookDao.getBookById(id)

    fun getBookFlow(id: String): Flow<BookEntity?> = bookDao.getBookByIdFlow(id)

    suspend fun insertBook(book: BookEntity) = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(id: String) = bookDao.deleteBookFully(id)

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    // --- Chapters ---
    fun getChaptersFlow(bookId: String): Flow<List<ChapterEntity>> = bookDao.getChaptersForBookFlow(bookId)

    suspend fun getChapters(bookId: String): List<ChapterEntity> = bookDao.getChaptersForBook(bookId)

    suspend fun getChapter(id: String): ChapterEntity? = bookDao.getChapterById(id)

    fun getChapterFlow(id: String): Flow<ChapterEntity?> = bookDao.getChapterByIdFlow(id)

    suspend fun insertChapter(chapter: ChapterEntity) = bookDao.insertChapter(chapter)

    suspend fun insertChapters(chapters: List<ChapterEntity>) = bookDao.insertChapters(chapters)

    suspend fun updateChapterReadStatus(chapterId: String, isRead: Boolean) = bookDao.updateChapterReadStatus(chapterId, isRead)

    suspend fun updateChaptersReadStatus(chapterIds: List<String>, isRead: Boolean) = bookDao.updateChaptersReadStatus(chapterIds, isRead)

    suspend fun getChapterCount(bookId: String): Int = bookDao.getChapterCountForBook(bookId)

    suspend fun getTotalChapterCount(): Int = bookDao.getTotalChapterCount()

    // --- Glossaries ---
    fun getGlossaryFlow(bookId: String): Flow<List<GlossaryEntity>> = bookDao.getGlossaryForBookFlow(bookId)

    suspend fun getGlossary(bookId: String): List<GlossaryEntity> = bookDao.getGlossaryForBook(bookId)

    suspend fun insertGlossary(glossary: GlossaryEntity) = bookDao.insertGlossary(glossary)

    suspend fun deleteGlossary(glossary: GlossaryEntity) = bookDao.deleteGlossary(glossary)

    suspend fun clearGlossary(bookId: String) = bookDao.clearGlossaryForBook(bookId)

    // --- Polished Chapters ---
    suspend fun getPolishedChapter(chapterId: String): PolishedChapterEntity? = bookDao.getPolishedChapter(chapterId)

    fun getPolishedChapterFlow(chapterId: String): Flow<PolishedChapterEntity?> = bookDao.getPolishedChapterFlow(chapterId)

    suspend fun insertPolishedChapter(polished: PolishedChapterEntity) = bookDao.insertPolishedChapter(polished)

    suspend fun deletePolishedChapter(chapterId: String) = bookDao.deletePolishedChapter(chapterId)

    // --- Chapter Recaps ---
    suspend fun getChapterRecap(chapterId: String): ChapterRecapEntity? = bookDao.getChapterRecap(chapterId)

    suspend fun insertChapterRecap(recap: ChapterRecapEntity) = bookDao.insertChapterRecap(recap)

    suspend fun deleteChapterRecap(chapterId: String) = bookDao.deleteChapterRecap(chapterId)

    // --- Bulk Updates / Find-and-Replace ---
    suspend fun updateChapterContent(id: String, content: String) = bookDao.updateChapterContent(id, content)

    // --- Bookmarks ---
    fun getBookmarksFlow(bookId: String): Flow<List<BookmarkEntity>> = bookDao.getBookmarksForBookFlow(bookId)

    suspend fun insertBookmark(bookmark: BookmarkEntity) = bookDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(id: String) = bookDao.deleteBookmarkById(id)

    // --- Unread Counts ---
    suspend fun getUnreadChapterCount(bookId: String): Int = bookDao.getUnreadChapterCount(bookId)

    fun getUnreadChapterCountFlow(bookId: String): Flow<Int> = bookDao.getUnreadChapterCountFlow(bookId)

    // --- Bulk Backup Helpers ---
    suspend fun getAllBookmarks(): List<BookmarkEntity> = bookDao.getAllBookmarks()
    suspend fun getAllGlossaries(): List<GlossaryEntity> = bookDao.getAllGlossaries()
    suspend fun insertBooks(books: List<BookEntity>) = bookDao.insertBooks(books)
    suspend fun insertGlossaries(glossaries: List<GlossaryEntity>) = bookDao.insertGlossaries(glossaries)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>) = bookDao.insertBookmarks(bookmarks)

    // --- Glossary application helper ---
    fun applyGlossary(text: String, glossary: List<GlossaryEntity>): String {
        var cleanText = text
        for (item in glossary) {
            if (item.originalText.isNotEmpty()) {
                cleanText = cleanText.replace(item.originalText, item.replacementText, ignoreCase = true)
            }
        }
        return cleanText
    }
}
