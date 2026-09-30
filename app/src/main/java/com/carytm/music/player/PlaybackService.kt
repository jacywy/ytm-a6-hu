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
import android.os.SystemClock
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
import com.carytm.music.util.LocaleHelper

class PlaybackService : Service(), MusicPlayer.PlaybackListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

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
            isPlaying = { MusicPlayer.isPlaying() },
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
        val initialNotification = buildNotification("CarYTM", getString(R.string.now_playing))
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
    private var currentDurationMs: Long = 0L

    override fun onSongChanged(song: SongItem?) {
        currentDurationMs = (song?.durationSec ?: 0L) * 1000L
        updatePlaybackState()
        updateMetadataAndNotification(song)
    }

    private fun updateMetadataAndNotification(song: SongItem?) {
        val title = song?.title ?: "CarYTM"
        val artist = song?.artist ?: getString(R.string.now_playing)
        val album = song?.albumName?.takeIf { it.isNotBlank() } ?: "YouTube Music"

        // Update MediaMetadataCompat for Car Dashboard, HUD, and Car Home Launchers
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs)

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
        } else {
            audioFocusManager.onUserPaused()
        }
        val song = MusicPlayer.getCurrentSong()
        val title = song?.title ?: "CarYTM"
        val artist = song?.artist ?: getString(R.string.now_playing)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, artist, currentAlbumArt))
    }

    override fun onBuffering(isBuffering: Boolean) {
        updatePlaybackState()
    }

    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {
        // 1. Update MediaMetadata duration if newly resolved or updated
        if (totalMs > 0 && currentDurationMs != totalMs) {
            currentDurationMs = totalMs
            val song = MusicPlayer.getCurrentSong()
            val title = song?.title ?: "CarYTM"
            val artist = song?.artist ?: getString(R.string.now_playing)
            val album = song?.albumName?.takeIf { it.isNotBlank() } ?: "YouTube Music"

            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalMs)

            currentAlbumArt?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
            }
            mediaSession.setMetadata(metadataBuilder.build())
        }

        // 2. Synchronize PlaybackState for car home launchers (e.g. DuduOS) and HUD
        val isPlaying = MusicPlayer.isPlaying()
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, currentMs, if (isPlaying) 1.0f else 0.0f, SystemClock.elapsedRealtime())
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    override fun onError(message: String) {
        // Log or show toast
    }

    private fun updatePlaybackState() {
        val isPlaying = MusicPlayer.isPlaying()
        val state = if (isPlaying) {
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
            .setState(state, MusicPlayer.getCurrentPosition(), if (isPlaying) 1.0f else 0.0f, SystemClock.elapsedRealtime())
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_prev, getString(R.string.notification_action_prev), prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) getString(R.string.notification_action_pause) else getString(R.string.notification_action_play),
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_next, getString(R.string.notification_action_next), nextPendingIntent)
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
        PlaybackStateManager.saveProgress(this, MusicPlayer.getCurrentPosition())
        MusicPlayer.removeListener(this)
        audioFocusManager.abandonAudioFocus()
        mediaSession.release()
    }
}
