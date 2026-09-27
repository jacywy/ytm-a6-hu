package com.carytm.music.net

import com.carytm.music.model.PlaylistItem
import com.carytm.music.model.SongItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object InnertubeApi {

    private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun createBaseContext(): JsonObject {
        val root = JsonObject()
        val context = JsonObject()
        val client = JsonObject()
        client.addProperty("clientName", "WEB_REMIX")
        client.addProperty("clientVersion", "1.20240101.01.00")
        client.addProperty("hl", "zh-CN")
        client.addProperty("gl", "US")
        context.add("client", client)
        root.add("context", context)
        return root
    }

    suspend fun search(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        try {
            val payload = createBaseContext()
            payload.addProperty("query", query)
            // Filter: Song
            payload.addProperty("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo%3D")

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext list
            val json = JsonParser.parseString(body).asJsonObject

            parseResponsiveItems(json, list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun getHomeRecommendations(): Pair<List<PlaylistItem>, List<SongItem>> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<PlaylistItem>()
        val songs = mutableListOf<SongItem>()
        try {
            val payload = createBaseContext()
            payload.addProperty("browseId", "FEmusic_home")

            val request = Request.Builder()
                .url("$BASE_URL/browse")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Pair(playlists, songs)
            val json = JsonParser.parseString(body).asJsonObject

            parseTwoRowItems(json, playlists)
            parseResponsiveItems(json, songs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext Pair(playlists, songs)
    }

    suspend fun getUserPlaylists(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaylistItem>()
        try {
            // First add standard "Liked Songs"
            list.add(
                PlaylistItem(
                    playlistId = "LM",
                    title = "我喜欢的音乐 (Liked Songs)",
                    author = "YouTube Music",
                    thumbnailUrl = "",
                    songCountText = "收藏歌曲"
                )
            )

            val payload = createBaseContext()
            payload.addProperty("browseId", "FEmusic_liked_playlists")

            val request = Request.Builder()
                .url("$BASE_URL/browse")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext list
            val json = JsonParser.parseString(body).asJsonObject

            parseTwoRowItems(json, list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun getPlaylistTracks(playlistId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        try {
            val payload = createBaseContext()
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            payload.addProperty("browseId", browseId)

            val request = Request.Builder()
                .url("$BASE_URL/browse")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext list
            val json = JsonParser.parseString(body).asJsonObject

            parseResponsiveItems(json, list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // Helper to extract tracks recursively from JSON tree
    private fun parseResponsiveItems(element: JsonObject, output: MutableList<SongItem>) {
        val jsonStr = element.toString()
        if (!jsonStr.contains("musicResponsiveListItemRenderer")) return

        fun traverse(obj: JsonObject) {
            if (obj.has("musicResponsiveListItemRenderer")) {
                val item = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                val song = extractSongItem(item)
                if (song != null) {
                    output.add(song)
                }
                return
            }
            for (key in obj.keySet()) {
                val child = obj.get(key)
                if (child != null && child.isJsonObject) {
                    traverse(child.asJsonObject)
                } else if (child != null && child.isJsonArray) {
                    for (arrElem in child.asJsonArray) {
                        if (arrElem.isJsonObject) traverse(arrElem.asJsonObject)
                    }
                }
            }
        }
        traverse(element)
    }

    private fun extractSongItem(item: JsonObject): SongItem? {
        try {
            val playlistItemData = item.getAsJsonObject("playlistItemData")
            val videoId = playlistItemData?.get("videoId")?.asString
                ?: extractVideoIdFromRuns(item)
                ?: return null

            val flexColumns = item.getAsJsonArray("flexColumns") ?: return null
            var title = ""
            var artist = ""
            var duration = ""
            var thumb = ""

            // Extract title
            if (flexColumns.size() > 0) {
                val firstCol = flexColumns[0].asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                title = extractTextFromRuns(firstCol)
            }

            // Extract artist and duration
            if (flexColumns.size() > 1) {
                val secondCol = flexColumns[1].asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                artist = extractTextFromRuns(secondCol)
            }

            // Extract thumbnail
            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")

            if (thumbnails != null && thumbnails.size() > 0) {
                thumb = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
            }

            if (title.isNotBlank()) {
                return SongItem(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    durationText = duration,
                    thumbnailUrl = thumb
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseTwoRowItems(element: JsonObject, output: MutableList<PlaylistItem>) {
        fun traverse(obj: JsonObject) {
            if (obj.has("musicTwoRowItemRenderer")) {
                val item = obj.getAsJsonObject("musicTwoRowItemRenderer")
                val browseEndpoint = item.getAsJsonObject("navigationEndpoint")
                    ?.getAsJsonObject("browseEndpoint")
                val browseId = browseEndpoint?.get("browseId")?.asString
                val titleObj = item.getAsJsonObject("title")
                val title = extractTextFromRuns(titleObj)
                val subtitleObj = item.getAsJsonObject("subtitle")
                val subtitle = extractTextFromRuns(subtitleObj)

                val thumbnails = item.getAsJsonObject("thumbnailRenderer")
                    ?.getAsJsonObject("musicThumbnailRenderer")
                    ?.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbnails != null && thumbnails.size() > 0) {
                    thumb = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }

                if (browseId != null && title.isNotBlank()) {
                    output.add(
                        PlaylistItem(
                            playlistId = browseId,
                            title = title,
                            author = subtitle,
                            thumbnailUrl = thumb
                        )
                    )
                }
                return
            }
            for (key in obj.keySet()) {
                val child = obj.get(key)
                if (child != null && child.isJsonObject) {
                    traverse(child.asJsonObject)
                } else if (child != null && child.isJsonArray) {
                    for (arrElem in child.asJsonArray) {
                        if (arrElem.isJsonObject) traverse(arrElem.asJsonObject)
                    }
                }
            }
        }
        traverse(element)
    }

    private fun extractTextFromRuns(textObj: JsonObject?): String {
        if (textObj == null) return ""
        val runs = textObj.getAsJsonArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.size()) {
            val run = runs[i].asJsonObject
            sb.append(run.get("text")?.asString ?: "")
        }
        return sb.toString().trim()
    }

    private fun extractVideoIdFromRuns(item: JsonObject): String? {
        val nav = item.getAsJsonObject("overlay")
            ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("musicPlayButtonRenderer")
            ?.getAsJsonObject("playNavigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")
        return nav?.get("videoId")?.asString
    }
}
