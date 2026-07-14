package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import org.jsoup.Jsoup

object EpubImporter {
    fun importEpub(context: Context, uri: Uri): Pair<BookEntity, List<ChapterEntity>>? {
        val contentResolver = context.contentResolver
        var zipInputStream: ZipInputStream? = null
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            zipInputStream = ZipInputStream(inputStream)
            
            var entry = zipInputStream.nextEntry
            val chapters = mutableListOf<ChapterEntity>()
            val bookId = "epub_${System.currentTimeMillis()}"
            var bookTitle = "Imported EPUB"
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (displayName.endsWith(".epub", ignoreCase = true)) {
                        bookTitle = displayName.substring(0, displayName.length - 5)
                    } else if (displayName.endsWith(".txt", ignoreCase = true)) {
                        bookTitle = displayName.substring(0, displayName.length - 4)
                    } else {
                        bookTitle = displayName
                    }
                }
            }

            var chapterNum = 1
            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".html", ignoreCase = true) || name.endsWith(".xhtml", ignoreCase = true)) {
                    val reader = BufferedReader(InputStreamReader(zipInputStream))
                    val sb = StringBuilder()
                    var line = reader.readLine()
                    while (line != null) {
                        sb.append(line).append("\n")
                        line = reader.readLine()
                    }
                    
                    val htmlContent = sb.toString()
                    val doc = Jsoup.parse(htmlContent)
                    val title = doc.title().trim().ifEmpty { "Chapter $chapterNum" }
                    val bodyText = doc.body()?.text() ?: ""
                    
                    if (bodyText.length > 100) {
                        val md5 = java.security.MessageDigest.getInstance("MD5")
                        val hash = md5.digest(bodyText.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                        chapters.add(
                            ChapterEntity(
                                id = "${bookId}_ch_$chapterNum",
                                bookId = bookId,
                                chapterId = "ch_$chapterNum",
                                chapterNumber = chapterNum,
                                title = title,
                                url = "local://$bookId/ch/$chapterNum",
                                content = bodyText,
                                hash = hash
                            )
                        )
                        chapterNum++
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            
            if (chapters.isEmpty()) {
                return null
            }

            chapters.sortBy { it.chapterNumber }

            val book = BookEntity(
                id = bookId,
                title = bookTitle,
                author = "Local Import",
                coverUrl = "",
                coverLocalPath = null,
                totalChapters = chapters.size,
                url = "local://$bookId",
                lastReadChapterId = chapters.first().id,
                synopsis = "Imported offline from local EPUB file."
            )

            return Pair(book, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                zipInputStream?.close()
            } catch (e: Exception) {}
        }
    }

    fun importTxt(context: Context, uri: Uri): Pair<BookEntity, List<ChapterEntity>>? {
        val contentResolver = context.contentResolver
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()

            val textContent = sb.toString()
            if (textContent.length < 100) return null

            var bookTitle = "Imported Book"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (displayName.endsWith(".txt", ignoreCase = true)) {
                        bookTitle = displayName.substring(0, displayName.length - 4)
                    } else {
                        bookTitle = displayName
                    }
                }
            }

            val bookId = "txt_${System.currentTimeMillis()}"
            val chapters = mutableListOf<ChapterEntity>()
            
            val patterns = listOf(
                Regex("(?i)chapter\\s+\\d+", RegexOption.IGNORE_CASE),
                Regex("(?i)chapter\\s+[ivxlcdm]+", RegexOption.IGNORE_CASE),
                Regex("(?i)第\\s*\\d+\\s*章", RegexOption.IGNORE_CASE)
            )

            var bestPattern: Regex? = null
            var bestCount = 0
            for (pattern in patterns) {
                val matches = pattern.findAll(textContent).count()
                if (matches > bestCount) {
                    bestCount = matches
                    bestPattern = pattern
                }
            }

            if (bestPattern != null && bestCount >= 2) {
                val matches = bestPattern.findAll(textContent).toList()
                for (i in matches.indices) {
                    val currentMatch = matches[i]
                    val start = currentMatch.range.first
                    val end = if (i + 1 < matches.size) matches[i + 1].range.first else textContent.length
                    
                    val title = currentMatch.value.trim()
                    val chapterContent = textContent.substring(start, end).trim()
                    val absoluteChapterNum = i + 1

                    if (chapterContent.length > 50) {
                        val md5 = java.security.MessageDigest.getInstance("MD5")
                        val hash = md5.digest(chapterContent.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                        chapters.add(
                            ChapterEntity(
                                id = "${bookId}_ch_$absoluteChapterNum",
                                bookId = bookId,
                                chapterId = "ch_$absoluteChapterNum",
                                chapterNumber = absoluteChapterNum,
                                title = title,
                                url = "local://$bookId/ch/$absoluteChapterNum",
                                content = chapterContent,
                                hash = hash
                            )
                        )
                    }
                }
            } else {
                val parts = textContent.chunked(8000)
                for ((index, part) in parts.withIndex()) {
                    val absoluteChapterNum = index + 1
                    val title = "Part $absoluteChapterNum"
                    val md5 = java.security.MessageDigest.getInstance("MD5")
                    val hash = md5.digest(part.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                    chapters.add(
                        ChapterEntity(
                            id = "${bookId}_ch_$absoluteChapterNum",
                            bookId = bookId,
                            chapterId = "ch_$absoluteChapterNum",
                            chapterNumber = absoluteChapterNum,
                            title = title,
                            url = "local://$bookId/ch/$absoluteChapterNum",
                            content = part.trim(),
                            hash = hash
                        )
                    )
                }
            }

            if (chapters.isEmpty()) return null

            val book = BookEntity(
                id = bookId,
                title = bookTitle,
                author = "Local Import",
                coverUrl = "",
                coverLocalPath = null,
                totalChapters = chapters.size,
                url = "local://$bookId",
                lastReadChapterId = chapters.first().id,
                synopsis = "Imported offline from local text file."
            )

            return Pair(book, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
