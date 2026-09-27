package com.carytm.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.carytm.music.R
import com.carytm.music.model.SongItem
import com.carytm.music.player.MusicPlayer
import com.carytm.music.player.PlaybackService

class MainActivity : AppCompatActivity(), MusicPlayer.PlaybackListener {

    private lateinit var navBtnHome: View
    private lateinit var navBtnLibrary: View
    private lateinit var navBtnSearch: View
    private lateinit var navBtnSettings: View

    private lateinit var miniPlayerContainer: View
    private lateinit var miniAlbumArt: ImageView
    private lateinit var miniSongTitle: TextView
    private lateinit var miniArtistName: TextView
    private lateinit var miniBtnPrev: ImageButton
    private lateinit var miniBtnPlayPause: ImageButton
    private lateinit var miniBtnNext: ImageButton

    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Start playback background service with media session
        PlaybackService.start(this)

        initViews()
        setupNavigation()
        setupMiniPlayer()

        MusicPlayer.addListener(this)

        // Default open Home
        switchFragment(HomeFragment(), navBtnHome)
    }

    private fun initViews() {
        navBtnHome = findViewById(R.id.nav_btn_home)
        navBtnLibrary = findViewById(R.id.nav_btn_library)
        navBtnSearch = findViewById(R.id.nav_btn_search)
        navBtnSettings = findViewById(R.id.nav_btn_settings)

        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniAlbumArt = findViewById(R.id.mini_album_art)
        miniSongTitle = findViewById(R.id.mini_song_title)
        miniArtistName = findViewById(R.id.mini_artist_name)
        miniBtnPrev = findViewById(R.id.mini_btn_prev)
        miniBtnPlayPause = findViewById(R.id.mini_btn_play_pause)
        miniBtnNext = findViewById(R.id.mini_btn_next)
    }

    private fun setupNavigation() {
        navBtnHome.setOnClickListener { switchFragment(HomeFragment(), navBtnHome) }
        navBtnLibrary.setOnClickListener { switchFragment(LibraryFragment(), navBtnLibrary) }
        navBtnSearch.setOnClickListener { switchFragment(SearchFragment(), navBtnSearch) }
        navBtnSettings.setOnClickListener { switchFragment(SettingsFragment(), navBtnSettings) }
    }

    private fun switchFragment(fragment: Fragment, selectedNavView: View) {
        activeFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        resetNavHighlight()
        selectedNavView.setBackgroundResource(R.drawable.bg_nav_item_selected)
    }

    private fun resetNavHighlight() {
        navBtnHome.background = null
        navBtnLibrary.background = null
        navBtnSearch.background = null
        navBtnSettings.background = null
    }

    private fun setupMiniPlayer() {
        miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        miniBtnPlayPause.setOnClickListener {
            MusicPlayer.togglePlayPause()
        }

        miniBtnNext.setOnClickListener {
            MusicPlayer.playNext()
        }

        miniBtnPrev.setOnClickListener {
            MusicPlayer.playPrevious()
        }

        updateMiniPlayerUI(MusicPlayer.getCurrentSong(), MusicPlayer.isPlaying())
    }

    private fun updateMiniPlayerUI(song: SongItem?, isPlaying: Boolean) {
        if (song != null) {
            miniSongTitle.text = song.title
            miniArtistName.text = song.artist
            if (song.thumbnailUrl.isNotBlank()) {
                Glide.with(this)
                    .load(song.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(miniAlbumArt)
            } else {
                miniAlbumArt.setImageResource(R.drawable.ic_music_placeholder)
            }
        }

        miniBtnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    override fun onSongChanged(song: SongItem?) {
        runOnUiThread {
            updateMiniPlayerUI(song, MusicPlayer.isPlaying())
        }
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            miniBtnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    override fun onBuffering(isBuffering: Boolean) {}

    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {}

    override fun onError(message: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.removeListener(this)
    }
}
