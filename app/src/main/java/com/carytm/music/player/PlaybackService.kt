package com.carytm.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
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
            onPauseRequested = { MusicPlayer.togglePlayPause() },
            onResumeRequested = { if (!MusicPlayer.isPlaying()) MusicPlayer.togglePlayPause() },
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
                        MusicPlayer.togglePlayPause()
                    }
                }

                override fun onPause() {
                    MusicPlayer.togglePlayPause()
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
        startForeground(NOTIFICATION_ID, buildNotification("CarYTM", "准备就绪"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSongChanged(song: SongItem?) {
        updatePlaybackState()
        val title = song?.title ?: "CarYTM"
        val artist = song?.artist ?: "正在播放"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, artist))
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {
        updatePlaybackState()
        if (isPlaying) {
            audioFocusManager.requestAudioFocus()
        }
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

    private fun buildNotification(title: String, artist: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_home)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.removeListener(this)
        audioFocusManager.abandonAudioFocus()
        mediaSession.release()
    }
}
