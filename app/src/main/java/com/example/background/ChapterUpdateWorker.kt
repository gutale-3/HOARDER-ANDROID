package com.example.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.scraper.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ChapterUpdateWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "chapter_updates_channel"
        const val NOTIFICATION_ID_BASE = 2000
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(context)
        val bookDao = database.bookDao()

        val books = withContext(Dispatchers.IO) {
            bookDao.getAllBooks()
        }

        if (books.isEmpty()) return Result.success()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chapter Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when new novel chapters are released"
            }
            notificationManager.createNotificationChannel(channel)
        }

        for ((index, book) in books.withIndex()) {
            try {
                val scraper = SourceManager.getSourceForUrl(book.url)
                val bookUrl = book.url

                val chapterUrls = withContext(Dispatchers.Main) {
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                    }
                    var urls = emptyList<String>()
                    try {
                        urls = scraper.scrapeChapterList(webView, bookUrl)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    urls
                }

                if (chapterUrls.isNotEmpty()) {
                    val savedChapterCount = withContext(Dispatchers.IO) {
                        bookDao.getChapterCountForBook(book.id)
                    }
                    val newCount = chapterUrls.size - savedChapterCount
                    if (newCount > 0) {
                        val openAppIntent = Intent(context, com.example.MainActivity::class.java)
                        val openAppPendingIntent = PendingIntent.getActivity(
                            context, index, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_popup_sync)
                            .setContentTitle("New Chapters for ${book.title}")
                            .setContentText("Found $newCount new chapters available to download!")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(openAppPendingIntent)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(NOTIFICATION_ID_BASE + index, notification)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(1000)
        }

        return Result.success()
    }
}
