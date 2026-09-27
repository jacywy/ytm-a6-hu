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
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

object StreamResolver {

    private var initialized = false

    fun init() {
        if (initialized) return
        NewPipe.init(object : Downloader() {
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

                val okResponse = NetworkClient.okHttpClient.newCall(reqBuilder.build()).execute()
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
        })
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
            if (audioStreams.isEmpty()) return@withContext null

            // Select stream by preference
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

            return@withContext selected?.content
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
