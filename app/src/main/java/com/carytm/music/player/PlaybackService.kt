package com.carytm.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.carytm.music.R
import com.carytm.music.model.SongItem
import com.carytm.music.ui.MainActivity

class PlaybackService : Service(), MusicPlayer.PlaybackListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioFocusManager: CarAudioFocusManager

    companion object {
        const val CHANNEL_ID = "carytm_playback_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        MusicPlayer.init(this)
        MusicPlayer.addListener(this)

        // Initialize Car AudioFocus Manager with Ducking
        audioFocusManager = CarAudioFocusManager(
            context = this,
            onPauseRequested = { MusicPlayer.pause() },
            onResumeRequested = { MusicPlayer.play() },
            onDuckRequested = { vol -> MusicPlayer.setVolume(vol) }
        )

        // Setup MediaSessionCompat for Car Steering Wheel Keys (方控按键)
        mediaSession = MediaSessionCompat(this, "CarYTMMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (audioFocusManager.requestAudioFocus()) {
                        MusicPlayer.play()
                    }
                }

                override fun onPause() {
                    MusicPlayer.pause()
                }

                override fun onSkipToNext() {
                    MusicPlayer.playNext()
                }

                override fun onSkipToPrevious() {
                    MusicPlayer.playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    MusicPlayer.seekTo(pos)
                }
            })
            isActive = true
        }

        createNotificationChannel()
        val initialNotification = buildNotification("CarYTM", "准备就绪")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var currentAlbumArt: Bitmap? = null

    override fun onSongChanged(song: SongItem?) {
        updatePlaybackState()
        updateMetadataAndNotification(song)
    }

    private fun updateMetadataAndNotification(song: SongItem?) {
        val title = song?.title ?: "CarYTM"
        val artist = song?.artist ?: "正在播放"
        val album = song?.albumName?.takeIf { it.isNotBlank() } ?: "YouTube Music"

        // Update MediaMetadataCompat for Car Dashboard, HUD, and Car Home Launchers
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (song?.durationSec ?: 0L) * 1000L)

        mediaSession.setMetadata(metadataBuilder.build())

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, artist, currentAlbumArt))

        // Asynchronously fetch album art bitmap for notification and car instrument cluster
        val thumbUrl = song?.thumbnailUrl
        if (!thumbUrl.isNullOrBlank()) {
            Glide.with(applicationContext)
                .asBitmap()
                .load(thumbUrl)
                .into(object : CustomTarget<Bitmap>(256, 256) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        currentAlbumArt = resource
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, resource)
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, resource)
                        mediaSession.setMetadata(metadataBuilder.build())
                        nm.notify(NOTIFICATION_ID, buildNotification(title, artist, resource))
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            currentAlbumArt = null
        }
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {
        updatePlaybackState()
        if (isPlaying) {
            audioFocusManager.requestAudioFocus()
        }
        val song = MusicPlayer.getCurrentSong()
        val title = song?.title ?: "CarYTM"
        val artist = song?.artist ?: "正在播放"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, artist, currentAlbumArt))
    }

    override fun onBuffering(isBuffering: Boolean) {
        updatePlaybackState()
    }

    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {
        // Can be used for lockscreen seekbar
    }

    override fun onError(message: String) {
        // Log or show toast
    }

    private fun updatePlaybackState() {
        val state = if (MusicPlayer.isPlaying()) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, MusicPlayer.getCurrentPosition(), 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CarYTM Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "车机音乐后台播放服务"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, artist: String, albumArt: Bitmap? = null): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val isPlaying = MusicPlayer.isPlaying()
        val prevPendingIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        val playPausePendingIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
        )
        val nextPendingIntent = androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_prev, "上一首", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_next, "下一首", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (albumArt != null) {
            builder.setLargeIcon(albumArt)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.removeListener(this)
        audioFocusManager.abandonAudioFocus()
        mediaSession.release()
    }
}
