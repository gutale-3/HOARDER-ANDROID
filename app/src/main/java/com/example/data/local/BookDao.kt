package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    // --- Books ---
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookInternal(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Transaction
    suspend fun insertBook(book: BookEntity) {
        val rowId = insertBookInternal(book)
        if (rowId == -1L) {
            updateBook(book)
        }
    }

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersByBookId(bookId: String)

    @Query("DELETE FROM glossaries WHERE bookId = :bookId")
    suspend fun deleteGlossariesByBookId(bookId: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksByBookId(bookId: String)

    @Query("DELETE FROM polished_chapters WHERE bookId = :bookId")
    suspend fun deletePolishedChaptersByBookId(bookId: String)

    @Query("DELETE FROM chapter_recaps WHERE bookId = :bookId")
    suspend fun deleteRecapsByBookId(bookId: String)

    @Transaction
    suspend fun deleteBookFully(bookId: String) {
        deleteBookById(bookId)
        deleteChaptersByBookId(bookId)
        deleteGlossariesByBookId(bookId)
        deleteBookmarksByBookId(bookId)
        deletePolishedChaptersByBookId(bookId)
        deleteRecapsByBookId(bookId)
    }

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    // --- Chapters ---
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterNumber ASC")
    fun getChaptersForBookFlow(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterNumber ASC")
    suspend fun getChaptersForBook(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterByIdFlow(id: String): Flow<ChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET isRead = :isRead WHERE id = :chapterId")
    suspend fun updateChapterReadStatus(chapterId: String, isRead: Boolean)

    @Query("UPDATE chapters SET isRead = :isRead WHERE id IN (:chapterIds)")
    suspend fun updateChaptersReadStatus(chapterIds: List<String>, isRead: Boolean)

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun getChapterCountForBook(bookId: String): Int

    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun getTotalChapterCount(): Int

    // --- Glossaries ---
    @Query("SELECT * FROM glossaries WHERE bookId = :bookId")
    fun getGlossaryForBookFlow(bookId: String): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossaries WHERE bookId = :bookId")
    suspend fun getGlossaryForBook(bookId: String): List<GlossaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossary(glossary: GlossaryEntity)

    @Delete
    suspend fun deleteGlossary(glossary: GlossaryEntity)

    @Query("DELETE FROM glossaries WHERE bookId = :bookId")
    suspend fun clearGlossaryForBook(bookId: String)

    // --- Polished Chapters ---
    @Query("SELECT * FROM polished_chapters WHERE chapterId = :chapterId")
    suspend fun getPolishedChapter(chapterId: String): PolishedChapterEntity?

    @Query("SELECT * FROM polished_chapters WHERE chapterId = :chapterId")
    fun getPolishedChapterFlow(chapterId: String): Flow<PolishedChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPolishedChapter(polished: PolishedChapterEntity)

    @Query("DELETE FROM polished_chapters WHERE chapterId = :chapterId")
    suspend fun deletePolishedChapter(chapterId: String)

    // --- Chapter Recaps ---
    @Query("SELECT * FROM chapter_recaps WHERE chapterId = :chapterId")
    suspend fun getChapterRecap(chapterId: String): ChapterRecapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterRecap(recap: ChapterRecapEntity)

    @Query("DELETE FROM chapter_recaps WHERE chapterId = :chapterId")
    suspend fun deleteChapterRecap(chapterId: String)

    // --- Bulk Updates / Find-and-Replace ---
    @Query("UPDATE chapters SET content = :content WHERE id = :id")
    suspend fun updateChapterContent(id: String, content: String)

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getBookmarksForBookFlow(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    // --- Unread Counts ---
    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId AND isRead = 0")
    suspend fun getUnreadChapterCount(bookId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId AND isRead = 0")
    fun getUnreadChapterCountFlow(bookId: String): Flow<Int>

    // --- Bulk Backup Queries ---
    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM glossaries")
    suspend fun getAllGlossaries(): List<GlossaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossaries(glossaries: List<GlossaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)
}
