package com.carytm.music.player

import android.content.Context
import android.net.Uri
import com.carytm.music.extractor.StreamResolver
import com.carytm.music.model.SongItem
import com.carytm.music.net.NetworkClient
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import kotlinx.coroutines.*
import java.io.File

object MusicPlayer {

    private var exoPlayer: ExoPlayer? = null
    private var simpleCache: SimpleCache? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val queue = mutableListOf<SongItem>()
    private var currentIndex = -1

    var preferOpus: Boolean = false
    private var isResolving = false

    interface PlaybackListener {
        fun onSongChanged(song: SongItem?)
        fun onPlayStateChanged(isPlaying: Boolean)
        fun onBuffering(isBuffering: Boolean)
        fun onProgressUpdate(currentMs: Long, totalMs: Long)
        fun onError(message: String)
    }

    private val listeners = mutableListOf<PlaybackListener>()

    fun addListener(l: PlaybackListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: PlaybackListener) {
        listeners.remove(l)
    }

    fun init(context: Context) {
        if (exoPlayer != null) return

        // 500MB Local Audio Cache on vehicle internal storage
        val cacheDir = File(context.cacheDir, "audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024)
        val databaseProvider = StandaloneDatabaseProvider(context)
        simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)

        val player = ExoPlayer.Builder(context).build()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, false) // CarAudioFocusManager handles focus

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listeners.forEach { it.onPlayStateChanged(isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = (playbackState == Player.STATE_BUFFERING)
                listeners.forEach { it.onBuffering(isBuffering) }

                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                listeners.forEach { it.onError(error.message ?: "播放失败，YouTube 协议可能已变动") }
            }
        })

        exoPlayer = player
        startProgressTracker()
    }

    private fun startProgressTracker() {
        scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val cur = player.currentPosition
                        val total = if (player.duration > 0) player.duration else 0
                        listeners.forEach { it.onProgressUpdate(cur, total) }
                    }
                }
                delay(1000L)
            }
        }
    }

    fun playQueue(songs: List<SongItem>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(songs)
        currentIndex = startIndex
        playCurrent()
    }

    fun playSingle(song: SongItem) {
        queue.clear()
        queue.add(song)
        currentIndex = 0
        playCurrent()
    }

    private fun playCurrent() {
        if (currentIndex !in queue.indices) return
        val song = queue[currentIndex]
        listeners.forEach {
            it.onSongChanged(song)
            it.onBuffering(true)
        }

        scope.launch {
            isResolving = true
            val audioUrl = StreamResolver.resolveAudioUrl(song.videoId, preferOpus)
            isResolving = false

            if (audioUrl.isNullOrBlank()) {
                listeners.forEach { it.onError("无法解析音频流，请检查网络或更新 Extractor 引擎") }
                return@launch
            }

            exoPlayer?.let { player ->
                val uri = Uri.parse(audioUrl)
                val okHttpFactory = OkHttpDataSource.Factory(NetworkClient.okHttpClient)
                val cacheFactory = CacheDataSource.Factory()
                    .setCache(simpleCache!!)
                    .setUpstreamDataSourceFactory(okHttpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
                    .createMediaSource(MediaItem.fromUri(uri))

                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()
            }
        }
    }

    fun playNext() {
        if (queue.isEmpty()) return
        currentIndex = (currentIndex + 1) % queue.size
        playCurrent()
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekRewind10s() {
        exoPlayer?.let {
            val target = (it.currentPosition - 10000L).coerceAtLeast(0)
            it.seekTo(target)
        }
    }

    fun seekForward10s() {
        exoPlayer?.let {
            val target = (it.currentPosition + 10000L).coerceAtMost(it.duration)
            it.seekTo(target)
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    fun getCurrentSong(): SongItem? {
        return if (currentIndex in queue.indices) queue[currentIndex] else null
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    fun getDuration(): Long = exoPlayer?.duration?.coerceAtLeast(0) ?: 0

    fun clearCache() {
        try {
            simpleCache?.keys?.forEach { key ->
                simpleCache?.removeResource(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
