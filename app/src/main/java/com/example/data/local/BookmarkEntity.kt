package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String, // bookId_chapterId_paragraphIndex
    val bookId: String,
    val chapterId: String,
    val paragraphIndex: Int,
    val text: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
