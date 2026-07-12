package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_recaps")
data class ChapterRecapEntity(
    @PrimaryKey val chapterId: String,
    val bookId: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
