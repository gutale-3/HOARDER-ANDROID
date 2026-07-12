package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "polished_chapters")
data class PolishedChapterEntity(
    @PrimaryKey val chapterId: String,
    val bookId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
