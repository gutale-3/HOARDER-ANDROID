package com.example.data.api

import com.example.viewmodel.DiscoveryItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

object AniListHelper {

    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    // Offline / Local Fallback Web Novel Catalog to support offline ranking/filtering
    val fallbackCatalog = listOf(
        DiscoveryItem(
            title = "Library of Heaven's Path",
            description = "Trope: System, Teacher, Cultivation, Comedy. A library clerk is reincarnated as an incompetent teacher with a library system that reveals the weaknesses of anything he looks at.",
            searchUrl = "https://tomatomtl.com/#/search?search=Library+of+Heaven+s+Path"
        ),
        DiscoveryItem(
            title = "Reverend Insanity",
            description = "Trope: Villain Protagonist, Time Travel, Cultivation, Ruthless. A dark and gritty epic of a cultivator who travels back 500 years with the Spring Autumn Cicada to achieve immortality.",
            searchUrl = "https://tomatomtl.com/#/search?search=Reverend+Insanity"
        ),
        DiscoveryItem(
            title = "Lord of the Mysteries",
            description = "Trope: Victorian Fantasy, Steampunk, Transmigration, Eldritch. Zhou Mingrui is transmigrated into a Victorian-era world filled with potions, tarot clubs, and mystical secrets.",
            searchUrl = "https://tomatomtl.com/#/search?search=Lord+of+the+Mysteries"
        ),
        DiscoveryItem(
            title = "Coiling Dragon",
            description = "Trope: Cultivation, Western Fantasy, Magic, Action. Linley Baruch discovers a ring carved with a dragon and begins his journey from a decaying noble family to a supreme deity.",
            searchUrl = "https://tomatomtl.com/#/search?search=Coiling+Dragon"
        ),
        DiscoveryItem(
            title = "The Legendary Mechanic",
            description = "Trope: Virtual Reality, Reincarnation, Sci-Fi, System. Han Xiao is transmigrated into the game world he played, becoming a low-level NPC mechanic before the game launched.",
            searchUrl = "https://tomatomtl.com/#/search?search=The+Legendary+Mechanic"
        ),
        DiscoveryItem(
            title = "I Shall Seal the Heavens",
            description = "Trope: Scholar, Cultivation, Xianxia, Humor. Meng Hao, a failed young scholar, is forcibly recruited into a cultivation sect and rises to carve his destiny.",
            searchUrl = "https://tomatomtl.com/#/search?search=I+Shall+Seal+the+Heavens"
        ),
        DiscoveryItem(
            title = "Omniscient Reader's Viewpoint",
            description = "Trope: Survival, Post-Apocalypse, Constellations, Time Loop. Kim Dokja is the sole reader of a web novel that suddenly comes to life, forcing him to use his knowledge of the story to survive.",
            searchUrl = "https://tomatomtl.com/#/search?search=Omniscient+Reader+s+Viewpoint"
        ),
        DiscoveryItem(
            title = "Solo Leveling",
            description = "Trope: Hunters, Leveling System, Necromancer, Action. Sung Jin-Woo, the weakest hunter of mankind, gains the unique ability to level up infinitely in a double dungeon.",
            searchUrl = "https://tomatomtl.com/#/search?search=Solo+Leveling"
        ),
        DiscoveryItem(
            title = "Martial World",
            description = "Trope: Cultivation, Martial Arts, Magic Cube, Geniuses. Lin Ming obtains a mysterious Magic Cube from the Divine Realm and starts his rise to the peak of martial arts.",
            searchUrl = "https://tomatomtl.com/#/search?search=Martial+World"
        ),
        DiscoveryItem(
            title = "The Kings Avatar",
            description = "Trope: eSports, Gaming, MMORPG, OP Protagonist. Ye Xiu, a legendary pro player of the game Glory, is kicked from his team and starts over from a new server in an internet cafe.",
            searchUrl = "https://tomatomtl.com/#/search?search=The+Kings+Avatar"
        )
    )

    suspend fun fetchPopularNovels(genre: String? = null): List<DiscoveryItem> {
        val query = """
            query (${'$'}genre: String) {
              Page(page: 1, perPage: 15) {
                media(genre: ${'$'}genre, type: MANGA, format: NOVEL, sort: POPULARITY_DESC) {
                  id
                  title {
                    english
                    romaji
                  }
                  description
                  genres
                  averageScore
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject()
        if (!genre.isNullOrEmpty()) {
            variables.put("genre", genre)
        }

        val jsonRequest = JSONObject()
        jsonRequest.put("query", query)
        jsonRequest.put("variables", variables)

        val requestBody = jsonRequest.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return fallbackCatalog
                }
                val bodyString = response.body?.string() ?: return fallbackCatalog
                val jsonResponse = JSONObject(bodyString)
                val data = jsonResponse.optJSONObject("data") ?: return fallbackCatalog
                val page = data.optJSONObject("Page") ?: return fallbackCatalog
                val mediaArray = page.optJSONArray("media") ?: return fallbackCatalog

                val results = mutableListOf<DiscoveryItem>()
                for (i in 0 until mediaArray.length()) {
                    val mediaObj = mediaArray.getJSONObject(i)
                    val titleObj = mediaObj.optJSONObject("title")
                    val title = titleObj?.optString("english")?.trim()
                        ?: titleObj?.optString("romaji")?.trim()
                        ?: "Unknown Web Novel"
                    
                    val descHtml = mediaObj.optString("description", "")
                    val description = descHtml.replace(Regex("<[^>]*>"), "").trim() // strip html
                    val genresList = mediaObj.optJSONArray("genres")
                    val genres = mutableListOf<String>()
                    if (genresList != null) {
                        for (j in 0 until genresList.length()) {
                            genres.add(genresList.getString(j))
                        }
                    }
                    val score = mediaObj.optInt("averageScore", 0)

                    val finalDesc = "Genres: ${genres.joinToString(", ")}. Score: $score/100. $description"
                    val searchUrl = "https://tomatomtl.com/#/search?search=${URLEncoder.encode(title, "UTF-8")}"
                    results.add(DiscoveryItem(title, finalDesc, searchUrl))
                }
                
                if (results.isEmpty()) fallbackCatalog else results
            }
        } catch (e: Exception) {
            fallbackCatalog
        }
    }
}
