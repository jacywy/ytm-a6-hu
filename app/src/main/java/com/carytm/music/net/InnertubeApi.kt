package com.carytm.music.net

import android.content.Context
import com.carytm.music.R
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
            // 1. Fetch TV Library landing (FEmy_youtube)
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

            // 2. Fetch TV All Playlists aggregation (FEplaylist_aggregation)
            try {
                val payload = createTvContext()
                payload.addProperty("browseId", "FEplaylist_aggregation")

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

        // Case B: Cookie Login (or if logged in)
        if (repo.hasCookies || repo.isLoggedIn) {
            try {
                val payload = createBaseContext()
                payload.addProperty("browseId", "FEmusic_liked_playlists")

                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/browse")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                if (repo.accessToken != null) {
                    requestBuilder.header("X-Use-OAuth", "true")
                }

                val response = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute()
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
        val cleanPlaylistId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"

        // 1. Try TV browse with OAuth if logged in, or standard TV client
        try {
            val payload = createTvContext()
            payload.addProperty("browseId", browseId)

            val requestBuilder = Request.Builder()
                .url("$TV_BASE_URL/browse")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (NetworkClient.accountRepo.accessToken != null) {
                requestBuilder.header("X-Use-OAuth", "true")
            }

            val response = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JsonParser.parseString(body).asJsonObject
                parseTvPlaylistItems(json, list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. If empty, try Web Music browse
        if (list.isEmpty()) {
            try {
                val payload = createBaseContext()
                payload.addProperty("browseId", browseId)

                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/browse")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                if (NetworkClient.accountRepo.accessToken != null) {
                    requestBuilder.header("X-Use-OAuth", "true")
                }

                val response = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute()
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
                val npSongs = StreamResolver.getPlaylistSongs(cleanPlaylistId)
                list.addAll(npSongs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext list.distinctBy { it.videoId }
    }

    private fun parseTvPlaylists(json: JsonObject, output: MutableList<PlaylistItem>, authorName: String = "Playlists") {
        val plRegex = Regex("""(PL[a-zA-Z0-9_-]{10,}|RD[a-zA-Z0-9_-]{10,}|LL|WL)""")

        fun traverse(elem: JsonObject) {
            // 1. Tile Renderer (Standard in YouTube TV)
            if (elem.has("tileRenderer")) {
                val tile = elem.getAsJsonObject("tileRenderer")
                val onSelect = tile.getAsJsonObject("onSelectCommand") ?: tile.getAsJsonObject("navigationEndpoint")
                val browseEndpoint = onSelect?.getAsJsonObject("browseEndpoint")
                val watchEndpoint = onSelect?.getAsJsonObject("watchEndpoint")

                val rawBrowseId = browseEndpoint?.get("browseId")?.asString
                val watchPlId = watchEndpoint?.get("playlistId")?.asString
                val params = browseEndpoint?.get("params")?.asString

                var playlistId: String? = null
                if (!watchPlId.isNullOrBlank()) {
                    playlistId = watchPlId
                } else if (!rawBrowseId.isNullOrBlank()) {
                    if (rawBrowseId.startsWith("VL")) {
                        playlistId = rawBrowseId.removePrefix("VL")
                    } else if (rawBrowseId.startsWith("PL") || rawBrowseId.startsWith("RD") || rawBrowseId.startsWith("FL") || rawBrowseId == "WL" || rawBrowseId == "LL" || rawBrowseId == "LM") {
                        playlistId = rawBrowseId
                    }
                }
                if (playlistId == null && params != null) {
                    playlistId = plRegex.find(params)?.value
                }

                val meta = tile.getAsJsonObject("metadata")?.getAsJsonObject("tileMetadataRenderer")
                val titleObj = meta?.get("title")
                val title = when {
                    titleObj == null -> ""
                    titleObj.isJsonPrimitive -> titleObj.asString.trim()
                    titleObj.isJsonObject -> {
                        val o = titleObj.asJsonObject
                        if (o.has("simpleText")) o.get("simpleText")?.asString?.trim() ?: ""
                        else extractTextFromRuns(o)
                    }
                    else -> ""
                }

                if (playlistId == null && title.isNotBlank()) {
                    if (title.contains("稍后观看") || title.contains("Watch Later", ignoreCase = true)) {
                        playlistId = "WL"
                    } else if (title.contains("顶过的视频") || title.contains("Liked", ignoreCase = true)) {
                        playlistId = "LL"
                    }
                }

                var author = authorName
                var songCountText = ""
                val lines = meta?.getAsJsonArray("lines")
                if (lines != null) {
                    for (i in 0 until lines.size()) {
                        val line = lines[i].asJsonObject.getAsJsonObject("lineRenderer")
                        val items = line?.getAsJsonArray("items")
                        if (items != null && items.size() > 0) {
                            val textObj = items[0].asJsonObject.getAsJsonObject("lineItemRenderer")?.getAsJsonObject("text")
                            val text = extractTextFromRuns(textObj)
                            if (text.isNotBlank()) {
                                if (i == 0) author = text
                                else if (i == 1) songCountText = text
                            }
                        }
                    }
                }

                var thumb = ""
                val header = tile.getAsJsonObject("header")?.getAsJsonObject("tileHeaderRenderer")
                val headerThumbs = header?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                if (headerThumbs != null && headerThumbs.size() > 0) {
                    thumb = headerThumbs[headerThumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }
                if (thumb.isBlank()) {
                    val tileThumbs = tile.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                    if (tileThumbs != null && tileThumbs.size() > 0) {
                        thumb = tileThumbs[tileThumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                if (playlistId != null && title.isNotBlank() && !title.equals("Playlists", ignoreCase = true) && title != "播放列表" && !title.contains("设置") && !title.contains("Settings")) {
                    output.add(
                        PlaylistItem(
                            playlistId = playlistId,
                            title = title,
                            author = author,
                            thumbnailUrl = thumb,
                            songCountText = songCountText
                        )
                    )
                }
            }

            // 2. Grid Playlist Renderer (standard YouTube grid)
            if (elem.has("gridPlaylistRenderer")) {
                val grid = elem.getAsJsonObject("gridPlaylistRenderer")
                val plId = grid.get("playlistId")?.asString
                val titleObj = grid.getAsJsonObject("title")
                val title = extractTextFromRuns(titleObj)
                val author = extractTextFromRuns(grid.getAsJsonObject("shortBylineText"))
                val countText = grid.getAsJsonObject("videoCountShortText")?.get("simpleText")?.asString
                    ?: extractTextFromRuns(grid.getAsJsonObject("videoCountText"))
                val thumbs = grid.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbs != null && thumbs.size() > 0) {
                    thumb = thumbs[thumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                if (plId != null && title.isNotBlank()) {
                    output.add(PlaylistItem(plId, title, author.takeIf { it.isNotBlank() } ?: authorName, thumb, countText))
                }
            }

            // 3. Playlist Renderer (standard YouTube list)
            if (elem.has("playlistRenderer")) {
                val pl = elem.getAsJsonObject("playlistRenderer")
                val plId = pl.get("playlistId")?.asString
                val titleObj = pl.getAsJsonObject("title")
                val title = extractTextFromRuns(titleObj)
                val author = extractTextFromRuns(pl.getAsJsonObject("shortBylineText"))
                val countText = pl.getAsJsonObject("videoCountShortText")?.get("simpleText")?.asString
                    ?: extractTextFromRuns(pl.getAsJsonObject("videoCountText"))
                val thumbs = pl.getAsJsonObject("thumbnails")?.getAsJsonArray("thumbnails")
                    ?: pl.getAsJsonObject("thumbnailRenderer")?.getAsJsonObject("playlistVideoThumbnailRenderer")?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbs != null && thumbs.size() > 0) {
                    thumb = thumbs[thumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                if (plId != null && title.isNotBlank()) {
                    output.add(PlaylistItem(plId, title, author.takeIf { it.isNotBlank() } ?: authorName, thumb, countText))
                }
            }

            // 4. Music Two Row Item Renderer
            if (elem.has("musicTwoRowItemRenderer")) {
                val item = elem.getAsJsonObject("musicTwoRowItemRenderer")
                val endpoint = item.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")
                val browseId = endpoint?.get("browseId")?.asString
                val plId = if (browseId != null && browseId.startsWith("VL")) browseId.removePrefix("VL") else browseId
                val title = extractTextFromRuns(item.getAsJsonObject("title"))
                val subtitle = extractTextFromRuns(item.getAsJsonObject("subtitle"))
                val thumbs = item.getAsJsonObject("thumbnailRenderer")?.getAsJsonObject("musicThumbnailRenderer")?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbs != null && thumbs.size() > 0) {
                    thumb = thumbs[thumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                if (plId != null && title.isNotBlank()) {
                    output.add(PlaylistItem(plId, title, subtitle.takeIf { it.isNotBlank() } ?: authorName, thumb))
                }
            }

            // Recursive traversal for nested JSON
            for (key in elem.keySet()) {
                val child = elem.get(key)
                if (child != null && child.isJsonObject) {
                    traverse(child.asJsonObject)
                } else if (child != null && child.isJsonArray) {
                    for (a in child.asJsonArray) {
                        if (a.isJsonObject) traverse(a.asJsonObject)
                    }
                }
            }
        }

        traverse(json)
    }

    private fun parseTvPlaylistItems(json: JsonObject, output: MutableList<SongItem>) {
        fun findTiles(elem: JsonObject) {
            if (elem.has("tileRenderer")) {
                val tile = elem.getAsJsonObject("tileRenderer")
                val onSelect = tile.getAsJsonObject("onSelectCommand") ?: tile.getAsJsonObject("navigationEndpoint")
                val videoId = onSelect?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString

                val meta = tile.getAsJsonObject("metadata")?.getAsJsonObject("tileMetadataRenderer")
                val titleObj = meta?.get("title")
                val title = when {
                    titleObj == null -> ""
                    titleObj.isJsonPrimitive -> titleObj.asString.trim()
                    titleObj.isJsonObject -> {
                        val obj = titleObj.asJsonObject
                        if (obj.has("simpleText")) obj.get("simpleText")?.asString?.trim() ?: ""
                        else extractTextFromRuns(obj)
                    }
                    else -> ""
                }

                var artist = ""
                val lineObj = meta?.getAsJsonArray("lines")
                if (lineObj != null && lineObj.size() > 0) {
                    val line = lineObj[0].asJsonObject.getAsJsonObject("lineRenderer")
                    val items = line?.getAsJsonArray("items")
                    if (items != null && items.size() > 0) {
                        val textObj = items[0].asJsonObject.getAsJsonObject("lineItemRenderer")?.getAsJsonObject("text")
                        artist = extractTextFromRuns(textObj)
                    }
                }

                // If artist is blank or "YouTube", try extracting from title formatted like "Artist - Song Title"
                if (artist.isBlank() || artist.equals("YouTube", ignoreCase = true)) {
                    if (title.contains(" - ")) {
                        val parts = title.split(" - ", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            artist = parts[0].trim()
                        }
                    } else if (title.contains(" — ")) {
                        val parts = title.split(" — ", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            artist = parts[0].trim()
                        }
                    } else if (title.contains("–")) {
                        val parts = title.split("–", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            artist = parts[0].trim()
                        }
                    }
                }
                if (artist.isBlank()) {
                    artist = "YouTube"
                }

                val header = tile.getAsJsonObject("header")?.getAsJsonObject("tileHeaderRenderer")
                val thumbnails = header?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                var thumb = ""
                if (thumbnails != null && thumbnails.size() > 0) {
                    thumb = thumbnails[thumbnails.size() - 1].asJsonObject.get("url")?.asString ?: ""
                }
                if (thumb.isBlank()) {
                    val tileThumbs = tile.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                    if (tileThumbs != null && tileThumbs.size() > 0) {
                        thumb = tileThumbs[tileThumbs.size() - 1].asJsonObject.get("url")?.asString ?: ""
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

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

            if ((artist.isBlank() || artist.equals("YouTube", ignoreCase = true)) && title.contains(" - ")) {
                val parts = title.split(" - ", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    artist = parts[0].trim()
                }
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
        if (textObj.has("simpleText")) {
            val st = textObj.get("simpleText")
            if (st != null && !st.isJsonNull) {
                return st.asString.trim()
            }
        }
        val runs = textObj.getAsJsonArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.size()) {
            val run = runs[i].asJsonObject
            sb.append(run.get("text")?.asString ?: "")
        }
        return sb.toString().trim()
    }
}
