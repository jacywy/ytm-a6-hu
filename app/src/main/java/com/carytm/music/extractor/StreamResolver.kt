package com.carytm.music.extractor

import com.carytm.music.net.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.carytm.music.model.SongItem
import java.io.IOException

object StreamResolver {

    private var initialized = false

    fun init() {
        if (initialized) return
        val downloader = object : Downloader() {
            override fun execute(request: Request): Response {
                val httpMethod = request.httpMethod()
                val url = request.url()
                val headers = request.headers()
                val dataToSend = request.dataToSend()

                val reqBuilder = okhttp3.Request.Builder().url(url)
                for ((key, values) in headers) {
                    for (v in values) {
                        reqBuilder.addHeader(key, v)
                    }
                }

                if (httpMethod.equals("POST", ignoreCase = true)) {
                    val body = (dataToSend ?: ByteArray(0)).toRequestBody()
                    reqBuilder.post(body)
                } else if (httpMethod.equals("HEAD", ignoreCase = true)) {
                    reqBuilder.head()
                } else {
                    reqBuilder.get()
                }

                val okResponse = NetworkClient.extractorOkHttpClient.newCall(reqBuilder.build()).execute()
                val responseBody = okResponse.body?.string() ?: ""
                val responseHeaders = mutableMapOf<String, List<String>>()
                for (name in okResponse.headers.names()) {
                    responseHeaders[name] = okResponse.headers.values(name)
                }

                return Response(
                    okResponse.code,
                    okResponse.message,
                    responseHeaders,
                    responseBody,
                    okResponse.request.url.toString()
                )
            }
        }
        try {
            NewPipe.init(downloader, Localization.DEFAULT)
        } catch (e: Throwable) {
            try {
                NewPipe.init(downloader)
            } catch (e2: Throwable) {
                e2.printStackTrace()
            }
        }
        initialized = true
    }

    /**
     * Resolves playable audio stream URL from YouTube videoId using NewPipeExtractor.
     * Deciphers cipher signature & n-sig without system WebView.
     */
    suspend fun resolveAudioUrl(videoId: String, preferOpus: Boolean = false): String? = withContext(Dispatchers.IO) {
        init()
        try {
            val service = ServiceList.YouTube
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = service.getStreamExtractor(watchUrl)
            extractor.fetchPage()

            val audioStreams: List<AudioStream> = extractor.audioStreams
            if (audioStreams.isNotEmpty()) {
                val selected = if (preferOpus) {
                    audioStreams.firstOrNull { it.format?.id == 251 }
                        ?: audioStreams.firstOrNull { it.format?.id == 140 }
                        ?: audioStreams.maxByOrNull { it.averageBitrate }
                } else {
                    // Default M4A 128kbps (itag 140) for low CPU & battery consumption on car SoC
                    audioStreams.firstOrNull { it.format?.id == 140 }
                        ?: audioStreams.firstOrNull { it.format?.id == 251 }
                        ?: audioStreams.maxByOrNull { it.averageBitrate }
                }

                val url = selected?.content
                if (!url.isNullOrBlank()) {
                    return@withContext url
                }
            }

            // Fallback: If separate audio streams are unavailable, use progressive video stream (e.g. 360p MP4)
            // ExoPlayer will play the embedded AAC audio stream seamlessly.
            val videoStreams = extractor.videoStreams
            if (!videoStreams.isNullOrEmpty()) {
                val fallbackVideo = videoStreams.firstOrNull { it.content?.isNotBlank() == true }
                if (fallbackVideo != null) {
                    return@withContext fallbackVideo.content
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Fallback native YouTube search using NewPipeExtractor
     */
    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        init()
        val results = mutableListOf<SongItem>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            for (item in searchExtractor.initialPage.items) {
                if (item is StreamInfoItem) {
                    val vid = item.url?.substringAfter("v=")?.substringBefore("&") ?: ""
                    if (vid.isNotBlank()) {
                        val duration = if (item.duration > 0) {
                            String.format("%d:%02d", item.duration / 60, item.duration % 60)
                        } else ""
                        val thumb = item.thumbnails?.lastOrNull()?.url ?: ""
                        results.add(
                            SongItem(
                                videoId = vid,
                                title = item.name ?: "未知歌曲",
                                artist = item.uploaderName ?: "YouTube",
                                durationText = duration,
                                durationSec = item.duration,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    /**
     * Native playlist tracks extraction using NewPipeExtractor
     */
    suspend fun getPlaylistSongs(playlistId: String): List<SongItem> = withContext(Dispatchers.IO) {
        init()
        val results = mutableListOf<SongItem>()
        try {
            val cleanId = playlistId.removePrefix("VL")
            val url = "https://www.youtube.com/playlist?list=$cleanId"
            val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
            extractor.fetchPage()
            for (item in extractor.initialPage.items) {
                if (item is StreamInfoItem) {
                    val vid = item.url?.substringAfter("v=")?.substringBefore("&") ?: ""
                    if (vid.isNotBlank()) {
                        val duration = if (item.duration > 0) {
                            String.format("%d:%02d", item.duration / 60, item.duration % 60)
                        } else ""
                        val thumb = item.thumbnails?.lastOrNull()?.url ?: ""
                        results.add(
                            SongItem(
                                videoId = vid,
                                title = item.name ?: "未知歌曲",
                                artist = item.uploaderName ?: "YouTube",
                                durationText = duration,
                                durationSec = item.duration,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }
}
