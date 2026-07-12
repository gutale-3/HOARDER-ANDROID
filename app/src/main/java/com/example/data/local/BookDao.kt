package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    // --- Books ---
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

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

    // --- Chapter Recaps ---
    @Query("SELECT * FROM chapter_recaps WHERE chapterId = :chapterId")
    suspend fun getChapterRecap(chapterId: String): ChapterRecapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterRecap(recap: ChapterRecapEntity)

    // --- Bulk Updates / Find-and-Replace ---
    @Query("UPDATE chapters SET content = :content WHERE id = :id")
    suspend fun updateChapterContent(id: String, content: String)
}
