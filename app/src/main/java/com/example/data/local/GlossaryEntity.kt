package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "glossaries",
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
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: String,
    val originalText: String,
    val replacementText: String
)
