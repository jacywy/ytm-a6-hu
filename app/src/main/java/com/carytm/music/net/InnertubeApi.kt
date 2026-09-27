package com.carytm.music.net

import android.content.Context
import com.carytm.music.auth.AccountRepository
import com.carytm.music.extractor.StreamResolver
import com.carytm.music.model.PlaylistItem
import com.carytm.music.model.SongItem
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object InnertubeApi {

    private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
    private const val TV_BASE_URL = "https://www.youtube.com/youtubei/v1"
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

    private fun createTvContext(): JsonObject {
        val root = JsonObject()
        val context = JsonObject()
        val client = JsonObject()
        client.addProperty("clientName", "TVHTML5")
        client.addProperty("clientVersion", "7.20230405.08.01")
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

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JsonParser.parseString(body).asJsonObject
                parseResponsiveItems(json, list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If Innertube returned 0 results, fallback to NewPipeExtractor native search!
        if (list.isEmpty()) {
            try {
                val fallbackList = StreamResolver.searchSongs(query)
                list.addAll(fallbackList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext list.distinctBy { it.videoId }
    }

    suspend fun getHomeRecommendations(): Pair<List<PlaylistItem>, List<SongItem>> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<PlaylistItem>()
        val songs = mutableListOf<SongItem>()
        try {
            // 1. Fetch Top Charts so Home is guaranteed to have hot music
            val chartsPayload = createBaseContext()
            chartsPayload.addProperty("browseId", "FEmusic_charts")
            val chartsReq = Request.Builder()
                .url("$BASE_URL/browse")
                .post(chartsPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val chartsResp = NetworkClient.okHttpClient.newCall(chartsReq).execute()
            chartsResp.body?.string()?.let { b ->
                val json = JsonParser.parseString(b).asJsonObject
                parseResponsiveItems(json, songs)
                parseTwoRowItems(json, playlists)
            }

            // 2. Fetch Home personalized / recommendations
            val homePayload = createBaseContext()
            homePayload.addProperty("browseId", "FEmusic_home")
            val homeReq = Request.Builder()
                .url("$BASE_URL/browse")
                .post(homePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val homeResp = NetworkClient.okHttpClient.newCall(homeReq).execute()
            homeResp.body?.string()?.let { b ->
                val json = JsonParser.parseString(b).asJsonObject
                parseResponsiveItems(json, songs)
                parseTwoRowItems(json, playlists)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback if songs still empty
        if (songs.isEmpty()) {
            try {
                val fallbackSongs = StreamResolver.searchSongs("Top Hits")
                songs.addAll(fallbackSongs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext Pair(playlists.distinctBy { it.playlistId }, songs.distinctBy { it.videoId })
    }

    suspend fun getUserPlaylists(context: Context): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaylistItem>()
        val repo = AccountRepository(context)

        // Standard Default Playlists
        list.add(
            PlaylistItem(
                playlistId = "LM",
                title = context.getString(R.string.liked_songs),
                author = "YouTube Music",
                thumbnailUrl = "",
                songCountText = ""
            )
        )
        list.add(
            PlaylistItem(
                playlistId = "WL",
                title = if (java.util.Locale.getDefault().language == "zh") "稍后观看 (Watch Later)" else "Watch Later",
                author = "YouTube",
                thumbnailUrl = "",
                songCountText = ""
            )
        )

        // Case A: Google TV OAuth Login
        repo.accessToken?.let {
            try {
                val payload = createTvContext()
                payload.addProperty("browseId", "FEmy_youtube")

                val request = Request.Builder()
                    .url("$TV_BASE_URL/browse")
                    .header("X-Use-OAuth", "true")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    parseTvPlaylists(json, list, context.getString(R.string.my_playlists))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Case B: Cookie Login
        if (repo.hasCookies) {
            try {
                val payload = createBaseContext()
                payload.addProperty("browseId", "FEmusic_liked_playlists")

                val request = Request.Builder()
                    .url("$BASE_URL/browse")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    parseTwoRowItems(json, list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext list.distinctBy { it.playlistId }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        val isPl = playlistId.startsWith("PL") || playlistId == "WL" || playlistId == "LM"

        // 1. If it's a YouTube / TV Playlist, try TV browse with OAuth
        if (isPl) {
            try {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                val payload = createTvContext()
                payload.addProperty("browseId", browseId)

                val request = Request.Builder()
                    .url("$TV_BASE_URL/browse")
                    .header("X-Use-OAuth", "true")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    parseTvPlaylistItems(json, list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. If empty, try Web Music browse
        if (list.isEmpty()) {
            try {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                val payload = createBaseContext()
                payload.addProperty("browseId", browseId)

                val request = Request.Builder()
                    .url("$BASE_URL/browse")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    parseResponsiveItems(json, list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to NewPipeExtractor for playlist scraping
        if (list.isEmpty()) {
            try {
                val npSongs = StreamResolver.getPlaylistSongs(playlistId)
                list.addAll(npSongs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext list.distinctBy { it.videoId }
    }

    private fun parseTvPlaylists(json: JsonObject, output: MutableList<PlaylistItem>, authorName: String = "Playlists") {
        val plRegex = Regex("""(PL[a-zA-Z0-9_-]{10,}|RD[a-zA-Z0-9_-]{10,})""")
        fun findTabs(elem: JsonObject) {
            if (elem.has("tabRenderer")) {
                val tab = elem.getAsJsonObject("tabRenderer")
                val title = if (tab.has("title")) {
                    val t = tab.get("title")
                    if (t.isJsonPrimitive) t.asString else extractTextFromRuns(t.asJsonObject)
                } else ""

                val endpoint = tab.getAsJsonObject("endpoint")?.getAsJsonObject("browseEndpoint")
                val params = endpoint?.get("params")?.asString

                val foundPl = if (params != null) plRegex.find(params)?.value else null
                val playlistId = foundPl ?: if (title.contains("稍后观看") || title.contains("Watch Later", ignoreCase = true)) "WL" else null

                val thumbnails = tab.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbnails != null && thumbnails.size() > 0) {
                    thumb = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }

                if (playlistId != null && title.isNotBlank() && title != "播放列表" && !title.equals("Playlists", ignoreCase = true)) {
                    output.add(
                        PlaylistItem(
                            playlistId = playlistId,
                            title = title,
                            author = authorName,
                            thumbnailUrl = thumb,
                            songCountText = ""
                        )
                    )
                }
            }
            for (key in elem.keySet()) {
                val child = elem.get(key)
                if (child != null && child.isJsonObject) findTabs(child.asJsonObject)
                else if (child != null && child.isJsonArray) {
                    for (a in child.asJsonArray) {
                        if (a.isJsonObject) findTabs(a.asJsonObject)
                    }
                }
            }
        }
        findTabs(json)
    }

    private fun parseTvPlaylistItems(json: JsonObject, output: MutableList<SongItem>) {
        fun findTiles(elem: JsonObject) {
            if (elem.has("tileRenderer")) {
                val tile = elem.getAsJsonObject("tileRenderer")
                val videoId = tile.getAsJsonObject("onSelectCommand")
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString

                val titleObj = tile.getAsJsonObject("metadata")
                    ?.getAsJsonObject("tileMetadataRenderer")
                    ?.get("title")
                val title = if (titleObj?.isJsonObject == true) {
                    val obj = titleObj.asJsonObject
                    obj.get("simpleText")?.asString ?: extractTextFromRuns(obj)
                } else ""

                val lineObj = tile.getAsJsonObject("metadata")
                    ?.getAsJsonObject("tileMetadataRenderer")
                    ?.getAsJsonArray("lines")
                var artist = "YouTube"
                if (lineObj != null && lineObj.size() > 0) {
                    val line = lineObj[0].asJsonObject.getAsJsonObject("lineRenderer")
                    val items = line?.getAsJsonArray("items")
                    if (items != null && items.size() > 0) {
                        val textObj = items[0].asJsonObject.getAsJsonObject("lineItemRenderer")?.getAsJsonObject("text")
                        artist = extractTextFromRuns(textObj).takeIf { it.isNotBlank() } ?: "YouTube"
                    }
                }

                val header = tile.getAsJsonObject("header")?.getAsJsonObject("tileHeaderRenderer")
                val thumbnails = header?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbnails != null && thumbnails.size() > 0) {
                    thumb = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }

                val overlays = header?.getAsJsonArray("thumbnailOverlays")
                var duration = ""
                if (overlays != null && overlays.size() > 0) {
                    val timeObj = overlays[0].asJsonObject.getAsJsonObject("thumbnailOverlayTimeStatusRenderer")
                    duration = timeObj?.getAsJsonObject("text")?.get("simpleText")?.asString ?: ""
                }

                if (videoId != null && title.isNotBlank()) {
                    output.add(
                        SongItem(
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            durationText = duration,
                            thumbnailUrl = thumb
                        )
                    )
                }
            }
            for (key in elem.keySet()) {
                val child = elem.get(key)
                if (child != null && child.isJsonObject) findTiles(child.asJsonObject)
                else if (child != null && child.isJsonArray) {
                    for (a in child.asJsonArray) {
                        if (a.isJsonObject) findTiles(a.asJsonObject)
                    }
                }
            }
        }
        findTiles(json)
    }

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
            val videoId = extractVideoId(item) ?: return null

            val flexColumns = item.getAsJsonArray("flexColumns")
            var title = ""
            var artist = ""
            var duration = ""
            var thumb = ""

            if (flexColumns != null) {
                if (flexColumns.size() > 0) {
                    val firstCol = flexColumns[0].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                    title = extractTextFromRuns(firstCol)
                }

                if (flexColumns.size() > 1) {
                    val secondCol = flexColumns[1].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                    artist = extractTextFromRuns(secondCol)
                }
            }

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
                    artist = artist.takeIf { it.isNotBlank() } ?: "YouTube Music",
                    durationText = duration,
                    thumbnailUrl = thumb
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractVideoId(item: JsonObject): String? {
        // 1. Check playlistItemData
        item.getAsJsonObject("playlistItemData")?.get("videoId")?.asString?.let { return it }

        // 2. Check overlay play button
        item.getAsJsonObject("overlay")
            ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("musicPlayButtonRenderer")
            ?.getAsJsonObject("playNavigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")
            ?.get("videoId")?.asString?.let { return it }

        // 3. Check flexColumns[0] runs navigationEndpoint
        val flexColumns = item.getAsJsonArray("flexColumns")
        if (flexColumns != null && flexColumns.size() > 0) {
            val runs = flexColumns[0].asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")
            if (runs != null && runs.size() > 0) {
                val vid = runs[0].asJsonObject
                    .getAsJsonObject("navigationEndpoint")
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString
                if (vid != null) return vid
            }
        }

        // 4. Check direct navigationEndpoint
        item.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")
            ?.get("videoId")?.asString?.let { return it }

        // 5. Fallback regex in json string
        val itemStr = item.toString()
        val match = Regex(""""videoId":\s*"([a-zA-Z0-9_-]{11})"""").find(itemStr)
        return match?.groupValues?.get(1)
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
}
