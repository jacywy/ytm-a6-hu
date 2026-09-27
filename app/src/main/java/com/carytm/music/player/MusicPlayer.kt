package com.carytm.music.player

import android.content.Context
import android.net.Uri
import com.carytm.music.R
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
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import kotlinx.coroutines.*
import java.io.File

object MusicPlayer {

    private var exoPlayer: ExoPlayer? = null
    private var simpleCache: SimpleCache? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resolveJob: Job? = null
    private var preloadJob: Job? = null

    private val queue = mutableListOf<SongItem>()
    private var currentIndex = -1

    var preferOpus: Boolean = false
    var isShuffle: Boolean = false
        private set
    private var nextShuffleIndex = -1

    private var isResolving = false

    // Cache recently resolved direct audio stream URLs in memory to avoid redundant extraction
    private val resolvedUrlCache = mutableMapOf<String, String>()

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
        appContext = context.applicationContext
        if (exoPlayer != null) return

        val cacheSizeMb = getCacheLimitMb(context)
        val cacheDir = File(context.cacheDir, "audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(cacheSizeMb * 1024 * 1024L)
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
                    appContext?.let { ctx ->
                        getCurrentSong()?.let { song ->
                            OfflineRepository.markSongFullyCached(ctx, song)
                        }
                        trimCacheIfNeeded(ctx)
                    }
                    playNext()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                // If offline failed, fallback to online stream
                val curSong = getCurrentSong()
                if (curSong != null && appContext?.let { OfflineRepository.isFullyCached(it, curSong.videoId) } == true) {
                    appContext?.let { OfflineRepository.markSongIncomplete(it, curSong) }
                    scope.launch {
                        delay(300)
                        playCurrent()
                    }
                    return
                }
                listeners.forEach { it.onError(error.message ?: (appContext?.getString(R.string.play_error) ?: "Playback failed")) }
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

                        // Mark fully cached if user reaches near the end of the song
                        if (total > 15000L && cur >= (total - 5000L)) {
                            appContext?.let { ctx ->
                                getCurrentSong()?.let { song ->
                                    OfflineRepository.markSongFullyCached(ctx, song)
                                }
                            }
                        }
                    }
                }
                delay(1000L)
            }
        }
    }


    fun setShuffle(enabled: Boolean) {
        isShuffle = enabled
        if (!enabled) {
            nextShuffleIndex = -1
        }
    }

    fun toggleShuffle(): Boolean {
        isShuffle = !isShuffle
        if (!isShuffle) {
            nextShuffleIndex = -1
        }
        return isShuffle
    }

    fun playQueue(songs: List<SongItem>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(songs)
        currentIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
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
        appContext?.let { OfflineRepository.markSongStarted(it, song) }

        // 1. Cut off current playing audio IMMEDIATELY so previous song stops
        exoPlayer?.stop()

        // 2. Immediately update UI state to new song and show buffering
        listeners.forEach {
            it.onSongChanged(song)
            it.onBuffering(true)
            it.onPlayStateChanged(false)
        }

        resolveJob?.cancel()
        preloadJob?.cancel()

        // Determine next shuffle candidate in advance so preloader and next button use the same song
        if (isShuffle && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            nextShuffleIndex = if (candidates.isNotEmpty()) candidates.random() else -1
        } else {
            nextShuffleIndex = -1
        }

        resolveJob = scope.launch {
            isResolving = true
            val isOffline = appContext?.let { OfflineRepository.isFullyCached(it, song.videoId) } == true
            val audioUrl = if (isOffline) {
                resolvedUrlCache[song.videoId] ?: "https://offline.carytm.internal/${song.videoId}"
            } else {
                resolvedUrlCache[song.videoId] ?: StreamResolver.resolveAudioUrl(song.videoId, preferOpus)
            }
            if (!audioUrl.isNullOrBlank() && !isOffline) {
                resolvedUrlCache[song.videoId] = audioUrl
            }
            isResolving = false

            if (!isActive) return@launch

            if (audioUrl.isNullOrBlank()) {
                listeners.forEach {
                    it.onBuffering(false)
                    it.onError(appContext?.getString(R.string.play_error) ?: "Cannot resolve audio stream")
                }
                return@launch
            }

            exoPlayer?.let { player ->
                val uri = Uri.parse(audioUrl)
                val okHttpFactory = OkHttpDataSource.Factory(NetworkClient.mediaOkHttpClient)
                val cacheFactory = CacheDataSource.Factory()
                    .setCache(simpleCache!!)
                    .setUpstreamDataSourceFactory(okHttpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setCustomCacheKey(song.videoId)
                    .build()

                val mediaSource = DefaultMediaSourceFactory(cacheFactory)
                    .createMediaSource(mediaItem)

                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()
            }

            // 3. Trigger background preloading of next song
            preloadNextSong()
        }
    }

    /**
     * Preload and cache the next track in background to eliminate pause/delay when switching songs.
     */
    private fun preloadNextSong() {
        preloadJob?.cancel()
        preloadJob = scope.launch(Dispatchers.IO) {
            if (queue.size <= 1) return@launch
            val nextIndex = if (isShuffle) {
                if (nextShuffleIndex in queue.indices) nextShuffleIndex else {
                    val candidates = queue.indices.filter { it != currentIndex }
                    if (candidates.isNotEmpty()) candidates.random() else return@launch
                }
            } else {
                (currentIndex + 1) % queue.size
            }
            val nextSong = queue[nextIndex]

            // Step 1: Pre-resolve audio stream URL
            val nextUrl = resolvedUrlCache[nextSong.videoId] ?: run {
                val resolved = StreamResolver.resolveAudioUrl(nextSong.videoId, preferOpus)
                if (!resolved.isNullOrBlank()) {
                    resolvedUrlCache[nextSong.videoId] = resolved
                }
                resolved
            }

            if (nextUrl.isNullOrBlank() || !isActive) return@launch

            // Step 2: Pre-cache full song directly into SimpleCache
            try {
                val uri = Uri.parse(nextUrl)
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setKey(nextSong.videoId)
                    .setPosition(0)
                    .setLength(C.LENGTH_UNSET.toLong())
                    .build()

                val okHttpFactory = OkHttpDataSource.Factory(NetworkClient.mediaOkHttpClient)
                val cacheDataSource = CacheDataSource.Factory()
                    .setCache(simpleCache!!)
                    .setUpstreamDataSourceFactory(okHttpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()

                val cacheWriter = CacheWriter(cacheDataSource, dataSpec, null, null)
                cacheWriter.cache()

                appContext?.let { ctx ->
                    OfflineRepository.markSongFullyCached(ctx, nextSong, dataSpec.length)
                    trimCacheIfNeeded(ctx)
                }
            } catch (e: Throwable) {
                // Non-critical: if cancelled or interrupted, record as incomplete
                appContext?.let { ctx ->
                    OfflineRepository.markSongIncomplete(ctx, nextSong)
                }
            }
        }
    }

    fun playNext() {
        if (queue.isEmpty()) return
        currentIndex = if (isShuffle && queue.size > 1) {
            if (nextShuffleIndex in queue.indices) {
                val chosen = nextShuffleIndex
                nextShuffleIndex = -1
                chosen
            } else {
                val candidates = queue.indices.filter { it != currentIndex }
                if (candidates.isNotEmpty()) candidates.random() else (currentIndex + 1) % queue.size
            }
        } else {
            (currentIndex + 1) % queue.size
        }
        playCurrent()
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
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

    fun getCacheLimitMb(context: Context): Int {
        val prefs = context.getSharedPreferences("carytm_settings", Context.MODE_PRIVATE)
        return prefs.getInt("pref_cache_size_mb", 500)
    }

    fun setCacheLimitMb(context: Context, sizeMb: Int) {
        val prefs = context.getSharedPreferences("carytm_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("pref_cache_size_mb", sizeMb).apply()
        try {
            simpleCache?.release()
            val cacheDir = File(context.cacheDir, "audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(sizeMb * 1024 * 1024L)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUsedCacheSizeMb(): Float {
        return try {
            (simpleCache?.cacheSpace ?: 0L) / (1024f * 1024f)
        } catch (e: Exception) {
            0f
        }
    }

    fun trimCacheIfNeeded(context: Context) {
        val limitMb = getCacheLimitMb(context)
        var usedMb = getUsedCacheSizeMb()
        if (usedMb <= limitMb) return

        // Priority 1: Purge all incomplete / fragmented tracks first!
        val incompleteIds = OfflineRepository.getIncompleteVideoIds(context)
        for (id in incompleteIds) {
            try {
                simpleCache?.removeResource(id)
                OfflineRepository.removeRecord(context, id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Also clean up any untracked cache resources
        val tracked = OfflineRepository.getAllTrackedVideoIds(context).toSet()
        simpleCache?.keys?.filter { it !in tracked }?.forEach { untrackedKey ->
            try {
                simpleCache?.removeResource(untrackedKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        usedMb = getUsedCacheSizeMb()
        if (usedMb <= limitMb) return

        // Priority 2: Purge oldest fully-cached tracks (LRU)
        val oldestRecords = OfflineRepository.getOldestFullyCachedRecords(context)
        for (record in oldestRecords) {
            // Keep the currently playing song safe
            if (record.videoId == getCurrentSong()?.videoId) continue

            try {
                simpleCache?.removeResource(record.videoId)
                OfflineRepository.removeRecord(context, record.videoId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            usedMb = getUsedCacheSizeMb()
            if (usedMb <= limitMb) break
        }
    }

    fun clearCache(context: Context? = null) {
        resolvedUrlCache.clear()
        try {
            simpleCache?.keys?.forEach { key ->
                simpleCache?.removeResource(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val ctx = context ?: appContext
        ctx?.let { OfflineRepository.clearAll(it) }
    }
}
