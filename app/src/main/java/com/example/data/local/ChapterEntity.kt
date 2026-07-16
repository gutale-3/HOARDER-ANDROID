package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class ChapterEntity(
    @PrimaryKey val id: String, // format: "bookId_chapterId"
    val bookId: String,
    val chapterId: String,
    val chapterNumber: Int,
    val title: String,
    val url: String,
    val content: String,
    val isRead: Boolean = false,
    val hash: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val readAt: Long? = null
)
