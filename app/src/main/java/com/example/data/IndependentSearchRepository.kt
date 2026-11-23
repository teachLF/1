package com.example.data

import android.net.Uri
import com.example.model.SearchSuggestion
import com.example.model.SuggestionSource
import com.example.model.VortexUrls
import com.example.model.WikiSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class IndependentSearchRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "VortexBrowser/1.0 (Android; Independent Search)")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    suspend fun fetchSuggestions(query: String): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val lang = resolveLanguageCode(trimmed)
        val url = "https://$lang.wikipedia.org/w/api.php?action=opensearch" +
            "&search=${Uri.encode(trimmed)}&limit=5&namespace=0&format=json"

        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONArray(body)
                if (root.length() < 4) return@withContext emptyList()
                val titles = root.getJSONArray(1)
                val urls = root.getJSONArray(3)
                buildList {
                    for (i in 0 until titles.length()) {
                        val title = titles.optString(i)
                        val pageUrl = urls.optString(i)
                        if (title.isNotBlank()) {
                            add(
                                SearchSuggestion(
                                    text = title,
                                    url = pageUrl.takeIf { it.isNotBlank() },
                                    source = SuggestionSource.WIKIPEDIA
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchIndependentKnowledge(query: String): Result<List<WikiSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

            val lang = resolveLanguageCode(trimmed)
            val url = "https://$lang.wikipedia.org/w/api.php" +
                "?action=query&generator=search&gsrsearch=${Uri.encode(trimmed)}" +
                "&gsrlimit=12&prop=extracts|pageimages|info&inprop=url" +
                "&exintro=1&explaintext=1&exsentences=2&piprop=thumbnail&pithumbsize=240&format=json"

            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext Result.success(emptyList())

                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query")
                        ?: return@withContext Result.success(emptyList())
                    val pagesObj = queryObj.optJSONObject("pages")
                        ?: return@withContext Result.success(emptyList())

                    val indexed = mutableListOf<Pair<Int, WikiSearchResult>>()
                    val keys = pagesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val page = pagesObj.optJSONObject(key) ?: continue
                        val pageId = page.optLong("pageid", 0L)
                        val index = page.optInt("index", Int.MAX_VALUE)
                        val title = page.optString("title", "")
                        val snippet = page.optString("extract", "").trim()
                        val fullUrl = page.optString("fullurl", "")
                            .ifBlank { "https://$lang.wikipedia.org/?curid=$pageId" }
                        val thumb = page.optJSONObject("thumbnail")?.optString("source")

                        if (title.isNotBlank()) {
                            indexed.add(
                                index to WikiSearchResult(
                                    pageId = pageId,
                                    title = title,
                                    snippet = snippet,
                                    url = fullUrl,
                                    thumbnailUrl = thumb?.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }

                    Result.success(indexed.sortedBy { it.first }.map { it.second })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun resolveLanguageCode(text: String): String {
        return if (VortexUrls.containsArabic(text) || Locale.getDefault().language == "ar") {
            "ar"
        } else {
            "en"
        }
    }
}
