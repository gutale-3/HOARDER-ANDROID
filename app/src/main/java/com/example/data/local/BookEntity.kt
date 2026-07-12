package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String, // TomatoMTL book ID (e.g. "123")
    val url: String,
    val title: String,
    val author: String,
    val synopsis: String,
    val coverUrl: String?,
    val coverLocalPath: String?,
    val lastReadChapterId: String?,
    val totalChapters: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
