package com.carytm.music.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.carytm.music.R
import com.carytm.music.model.SongItem
import com.carytm.music.player.MusicPlayer
import com.carytm.music.util.LocaleHelper

class PlayerActivity : AppCompatActivity(), MusicPlayer.PlaybackListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var btnBack: View
    private lateinit var ivArtwork: ImageView
    private lateinit var btnLike: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvStatus: TextView
    private lateinit var seekbar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnRewind: Button
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnForward: Button

    private var isUserTrackingSeekBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initViews()
        setupListeners()

        MusicPlayer.addListener(this)
        updateUI(MusicPlayer.getCurrentSong(), MusicPlayer.isPlaying())
    }

    private fun initViews() {
        btnBack = findViewById(R.id.player_btn_back)
        ivArtwork = findViewById(R.id.player_artwork)
        btnLike = findViewById(R.id.player_btn_like)
        btnShuffle = findViewById(R.id.player_btn_shuffle)
        tvTitle = findViewById(R.id.player_title)
        tvArtist = findViewById(R.id.player_artist)
        tvStatus = findViewById(R.id.player_status_info)
        seekbar = findViewById(R.id.player_seekbar)
        tvCurrentTime = findViewById(R.id.player_time_current)
        tvTotalTime = findViewById(R.id.player_time_total)
        btnRewind = findViewById(R.id.player_btn_rewind)
        btnPrev = findViewById(R.id.player_btn_prev)
        btnPlayPause = findViewById(R.id.player_btn_play_pause)
        btnNext = findViewById(R.id.player_btn_next)
        btnForward = findViewById(R.id.player_btn_forward)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnShuffle.setOnClickListener {
            val enabled = MusicPlayer.toggleShuffle()
            updateShuffleUI(enabled)
            val msg = if (enabled) getString(R.string.shuffle_enabled_toast) else getString(R.string.shuffle_disabled_toast)
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        btnPlayPause.setOnClickListener {
            MusicPlayer.togglePlayPause()
        }

        btnNext.setOnClickListener {
            MusicPlayer.playNext()
        }

        btnPrev.setOnClickListener {
            MusicPlayer.playPrevious()
        }

        btnRewind.setOnClickListener {
            MusicPlayer.seekRewind10s()
        }

        btnForward.setOnClickListener {
            MusicPlayer.seekForward10s()
        }

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = MusicPlayer.getDuration()
                    val targetMs = (duration * (progress / 1000f)).toLong()
                    tvCurrentTime.text = formatTime(targetMs)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserTrackingSeekBar = false
                val duration = MusicPlayer.getDuration()
                val targetMs = (duration * ((sb?.progress ?: 0) / 1000f)).toLong()
                MusicPlayer.seekTo(targetMs)
            }
        })
    }

    private fun updateUI(song: SongItem?, isPlaying: Boolean) {
        if (song != null) {
            if (tvTitle.text != song.title) {
                tvTitle.text = song.title
            }
            if (tvArtist.text != song.artist) {
                tvArtist.text = song.artist
            }
            tvTitle.isSelected = true
            tvArtist.isSelected = true

            if (song.thumbnailUrl.isNotBlank()) {
                Glide.with(this)
                    .load(song.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(ivArtwork)
            } else {
                ivArtwork.setImageResource(R.drawable.ic_music_placeholder)
            }
        }

        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        updateShuffleUI(MusicPlayer.isShuffle)
    }

    private fun updateShuffleUI(isShuffle: Boolean) {
        val color = if (isShuffle) {
            androidx.core.content.ContextCompat.getColor(this, R.color.primary)
        } else {
            androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        }
        btnShuffle.setColorFilter(color)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onSongChanged(song: SongItem?) {
        runOnUiThread {
            updateUI(song, MusicPlayer.isPlaying())
        }
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    override fun onBuffering(isBuffering: Boolean) {
        runOnUiThread {
            tvStatus.text = if (isBuffering) getString(R.string.playing_loading) else getString(R.string.now_playing)
        }
    }

    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {
        if (!isUserTrackingSeekBar) {
            runOnUiThread {
                if (totalMs > 0) {
                    val progress = ((currentMs.toDouble() / totalMs.toDouble()) * 1000).toInt()
                    seekbar.progress = progress
                    tvCurrentTime.text = formatTime(currentMs)
                    tvTotalTime.text = formatTime(totalMs)
                }
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            tvStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.removeListener(this)
    }
}
