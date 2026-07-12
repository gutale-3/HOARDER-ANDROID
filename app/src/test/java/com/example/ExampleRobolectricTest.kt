package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.GlossaryEntity
import com.example.data.repository.NovelRepository
import com.example.data.ai.AiProviderRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: NovelRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NovelRepository(db.bookDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAppNameIsNovelHoarder() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Novel Hoarder", appName)
    }

    @Test
    fun testApplyGlossaryReplaceWorksCorrectly() {
        val originalText = "The translator did a quick job on tomato near the river."
        val glossary = listOf(
            GlossaryEntity(id = 1, bookId = "123", originalText = "tomato", replacementText = "Red Sweet Fruit"),
            GlossaryEntity(id = 2, bookId = "123", originalText = "river", replacementText = "Flowing Creek")
        )
        val cleanText = repository.applyGlossary(originalText, glossary)
        assertEquals("The translator did a quick job on Red Sweet Fruit near the Flowing Creek.", cleanText)
    }

    @Test
    fun testDatabaseMigrationSchemaUpgrade1to2() {
        // Create an open helper at version 1 to simulate original schema
        val openHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `synopsis` TEXT NOT NULL, `coverUrl` TEXT, `coverLocalPath` TEXT, `lastReadChapterId` TEXT, `totalChapters` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val dbWritable = openHelper.writableDatabase

        // Apply migration
        AppDatabase.MIGRATION_1_2.migrate(dbWritable)

        // Verify the newly migrated tables are present and can be queried
        val cursor1 = dbWritable.query("SELECT * FROM polished_chapters")
        assertNotNull(cursor1)
        cursor1.close()

        val cursor2 = dbWritable.query("SELECT * FROM chapter_recaps")
        assertNotNull(cursor2)
        cursor2.close()

        dbWritable.close()
    }

    @Test
    fun testAiProviderRegistrySelectsAppropriateFallbackAndAvailability() = runBlocking {
        val registry = AiProviderRegistry(context)
        
        // 1. Local provider is unavailable by default since gemma.task doesn't exist in in-memory test filesDir
        val localFile = File(context.filesDir, "gemma.task")
        if (localFile.exists()) {
            localFile.delete()
        }
        assertFalse(registry.localProvider.isAvailable())

        // 2. Test fallback routing
        val activeProvider = registry.getActiveProviderForTask("mediapipe_local", requiresJson = false)
        assertNotNull(activeProvider)
        // Since local is unavailable, active should fall back to cloud or mlkit depending on environment availability
        assertTrue(activeProvider.id == "gemini_cloud" || activeProvider.id == "mlkit_genai")
    }

    @Test
    fun testMlKitProviderPromptRouting() = runBlocking {
        val provider = com.example.data.ai.MlKitGenAiProvider(context)
        
        // If ML Kit provider is compatible in this testing environment, let's test routing
        if (provider.isAvailable()) {
            val recapResponse = provider.generate("Give me a recap of chapter 1", jsonMode = false)
            assertTrue(recapResponse.contains("On-Device Summarization"))

            val polishResponse = provider.generate("Please polish this chapter", jsonMode = false)
            assertTrue(polishResponse.contains("On-Device Rewriting"))

            val fallbackResponse = provider.generate("Do something else", jsonMode = false)
            assertTrue(fallbackResponse.contains("does not support generic prompts"))
        }
    }
}
