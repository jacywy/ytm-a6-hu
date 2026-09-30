package com.carytm.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.carytm.music.R
import com.carytm.music.auth.AccountRepository
import com.carytm.music.model.PlaylistItem
import com.carytm.music.model.SongItem
import com.carytm.music.net.InnertubeApi
import com.carytm.music.player.MusicPlayer
import com.carytm.music.player.OfflineRepository
import com.carytm.music.ui.adapter.PlaylistAdapter
import com.carytm.music.ui.adapter.SongAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(), MusicPlayer.PlaybackListener {

    private var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var layoutPlaylistsContainer: View
    private lateinit var cardOfflineMusic: View
    private lateinit var tvOfflineCount: TextView
    private lateinit var btnQuickOfflinePlay: Button
    private lateinit var bannerLoginPrompt: View
    private lateinit var tvAccountStatus: TextView
    private lateinit var btnLoginTrigger: Button
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var playlistAdapter: PlaylistAdapter

    // Playlist Detail Views
    private lateinit var layoutPlaylistDetail: View
    private lateinit var btnDetailBack: View
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailSubtitle: TextView
    private lateinit var btnDetailPlayAll: Button
    private lateinit var btnDetailShuffle: Button
    private lateinit var pbDetailLoading: ProgressBar
    private lateinit var tvDetailEmpty: TextView
    private lateinit var rvPlaylistTracks: RecyclerView
    private lateinit var songAdapter: SongAdapter

    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadTracksJob: Job? = null
    private val playlistList = mutableListOf<PlaylistItem>()
    private val currentTracks = mutableListOf<SongItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        
        layoutPlaylistsContainer = view.findViewById(R.id.layout_playlists_container)
        cardOfflineMusic = view.findViewById(R.id.card_offline_music)
        tvOfflineCount = view.findViewById(R.id.tv_offline_count)
        btnQuickOfflinePlay = view.findViewById(R.id.btn_quick_offline_play)
        bannerLoginPrompt = view.findViewById(R.id.banner_login_prompt)
        tvAccountStatus = view.findViewById(R.id.tv_account_status)
        btnLoginTrigger = view.findViewById(R.id.btn_login_trigger)
        rvPlaylists = view.findViewById(R.id.rv_library_playlists)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_library)
        swipeRefresh?.setColorSchemeResources(R.color.primary)
        swipeRefresh?.setOnRefreshListener {
            refreshData(isSwipe = true)
        }

        layoutPlaylistDetail = view.findViewById(R.id.layout_playlist_detail)
        btnDetailBack = view.findViewById(R.id.btn_detail_back)
        tvDetailTitle = view.findViewById(R.id.tv_detail_title)
        tvDetailSubtitle = view.findViewById(R.id.tv_detail_subtitle)
        btnDetailPlayAll = view.findViewById(R.id.btn_detail_play_all)
        btnDetailShuffle = view.findViewById(R.id.btn_detail_shuffle)
        pbDetailLoading = view.findViewById(R.id.pb_detail_loading)
        tvDetailEmpty = view.findViewById(R.id.tv_detail_empty)
        rvPlaylistTracks = view.findViewById(R.id.rv_playlist_tracks)

        cardOfflineMusic.setOnClickListener {
            openOfflineDetail()
        }

        btnQuickOfflinePlay.setOnClickListener {
            val list = OfflineRepository.getFullyCachedSongs(requireContext())
            if (list.isNotEmpty()) {
                MusicPlayer.setShuffle(true)
                MusicPlayer.playQueue(list.shuffled(), 0)
                Toast.makeText(context, getString(R.string.offline_quick_play_toast_format, list.size), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, getString(R.string.offline_empty_toast), Toast.LENGTH_SHORT).show()
            }
        }

        // 2-column grid for car landscape screen
        rvPlaylists.layoutManager = GridLayoutManager(context, 2)
        playlistAdapter = PlaylistAdapter { playlist ->
            openPlaylistDetail(playlist)
        }
        rvPlaylists.adapter = playlistAdapter

        // Tracks list in detail view
        rvPlaylistTracks.layoutManager = LinearLayoutManager(context)
        songAdapter = SongAdapter { song, position ->
            if (currentTracks.isNotEmpty()) {
                MusicPlayer.setShuffle(false)
                MusicPlayer.playQueue(currentTracks, position)
            }
        }
        rvPlaylistTracks.adapter = songAdapter

        btnDetailBack.setOnClickListener {
            closePlaylistDetail()
        }

        btnDetailPlayAll.setOnClickListener {
            if (currentTracks.isNotEmpty()) {
                MusicPlayer.setShuffle(false)
                MusicPlayer.playQueue(currentTracks, 0)
                Toast.makeText(context, getString(R.string.play_seq_toast_format, currentTracks.size), Toast.LENGTH_SHORT).show()
            }
        }

        btnDetailShuffle.setOnClickListener {
            if (currentTracks.isNotEmpty()) {
                MusicPlayer.setShuffle(true)
                MusicPlayer.playQueue(currentTracks.shuffled(), 0)
                Toast.makeText(context, getString(R.string.play_shuffle_toast_format, currentTracks.size), Toast.LENGTH_SHORT).show()
            }
        }

        btnLoginTrigger.setOnClickListener {
            showLoginDialog()
        }

        MusicPlayer.addListener(this)
        refreshData()
        return view
    }

    private fun openPlaylistDetail(playlist: PlaylistItem) {
        layoutPlaylistsContainer.visibility = View.GONE
        layoutPlaylistDetail.visibility = View.VISIBLE

        tvDetailTitle.text = playlist.title
        tvDetailSubtitle.text = "${playlist.author} • " + getString(R.string.loading_tracks)
        pbDetailLoading.visibility = View.VISIBLE
        tvDetailEmpty.visibility = View.GONE
        currentTracks.clear()
        songAdapter.submitList(emptyList())

        loadTracksJob?.cancel()
        loadTracksJob = scope.launch {
            val tracks = InnertubeApi.getPlaylistTracks(playlist.playlistId)
            pbDetailLoading.visibility = View.GONE
            val finalTracks = if (playlist.playlistId == "LM") {
                val ctx = context
                if (tracks.isNotEmpty() && ctx != null) {
                    com.carytm.music.player.LikedRepository.syncFromRemote(ctx, tracks)
                }
                val localLiked = if (ctx != null) com.carytm.music.player.LikedRepository.getLikedSongs(ctx) else emptyList()
                (tracks + localLiked).distinctBy { it.videoId }
            } else {
                tracks
            }

            if (finalTracks.isNotEmpty()) {
                currentTracks.clear()
                currentTracks.addAll(finalTracks)
                tvDetailSubtitle.text = getString(R.string.tracks_count_format, playlist.author, finalTracks.size)
                songAdapter.submitList(currentTracks)
                songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)
                scrollToCurrentPlayingTrack()
                tvDetailEmpty.visibility = View.GONE
            } else {
                tvDetailSubtitle.text = getString(R.string.tracks_count_format, playlist.author, 0)
                tvDetailEmpty.visibility = View.VISIBLE
            }
        }
    }

    fun closePlaylistDetail(): Boolean {
        if (layoutPlaylistDetail.visibility == View.VISIBLE) {
            layoutPlaylistDetail.visibility = View.GONE
            layoutPlaylistsContainer.visibility = View.VISIBLE
            loadTracksJob?.cancel()
            return true
        }
        return false
    }

    fun handleBackPressed(): Boolean {
        return closePlaylistDetail()
    }

    private fun scrollToCurrentPlayingTrack() {
        if (layoutPlaylistDetail.visibility != View.VISIBLE) return
        val currentVideoId = MusicPlayer.getCurrentSong()?.videoId ?: return
        val index = currentTracks.indexOfFirst { it.videoId == currentVideoId }
        if (index >= 0) {
            val lm = rvPlaylistTracks.layoutManager as? LinearLayoutManager ?: return
            val first = lm.findFirstCompletelyVisibleItemPosition()
            val last = lm.findLastCompletelyVisibleItemPosition()
            if (index < first || index > last) {
                lm.scrollToPositionWithOffset(index, 40)
            }
        }
    }

    override fun onSongChanged(song: SongItem?) {
        activity?.runOnUiThread {
            songAdapter.setCurrentPlaying(song?.videoId)
            scrollToCurrentPlayingTrack()
        }
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {}
    override fun onBuffering(isBuffering: Boolean) {}
    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {}
    override fun onError(message: String) {}

    override fun onDestroyView() {
        super.onDestroyView()
        MusicPlayer.removeListener(this)
        loadTracksJob?.cancel()
    }

    private fun showLoginDialog() {
        val dialog = LoginDialog(requireContext()) {
            Toast.makeText(context, getString(R.string.login_syncing_playlists_toast), Toast.LENGTH_SHORT).show()
            refreshData()
        }
        dialog.show()
    }

    private fun openOfflineDetail() {
        layoutPlaylistsContainer.visibility = View.GONE
        layoutPlaylistDetail.visibility = View.VISIBLE

        tvDetailTitle.text = getString(R.string.offline_title)
        pbDetailLoading.visibility = View.GONE
        loadTracksJob?.cancel()

        val list = OfflineRepository.getFullyCachedSongs(requireContext())
        currentTracks.clear()
        if (list.isNotEmpty()) {
            currentTracks.addAll(list)
            tvDetailSubtitle.text = getString(R.string.offline_detail_desc_format, list.size)
            tvDetailEmpty.visibility = View.GONE
            songAdapter.submitList(currentTracks)
            songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)
            scrollToCurrentPlayingTrack()
        } else {
            tvDetailSubtitle.text = getString(R.string.tracks_count_simple, 0)
            tvDetailEmpty.visibility = View.VISIBLE
            tvDetailEmpty.text = getString(R.string.offline_empty_hint)
            songAdapter.submitList(emptyList())
        }
    }

    private fun updateOfflineCardUI() {
        context?.let { ctx ->
            val count = OfflineRepository.getFullyCachedCount(ctx)
            tvOfflineCount.text = getString(R.string.offline_card_desc_format, count)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOfflineCardUI()
    }

    fun refreshData(isSwipe: Boolean = false) {
        updateOfflineCardUI()
        val repo = AccountRepository(requireContext())
        if (repo.isLoggedIn) {
            bannerLoginPrompt.visibility = View.GONE
            loadUserPlaylists(isSwipe)
        } else {
            swipeRefresh?.isRefreshing = false
            bannerLoginPrompt.visibility = View.VISIBLE
            tvAccountStatus.text = getString(R.string.not_logged_in)
            playlistList.clear()
            playlistAdapter.submitList(playlistList)
        }
    }

    private fun loadUserPlaylists(isSwipe: Boolean = false) {
        scope.launch {
            if (isSwipe) {
                val repo = AccountRepository(requireContext())
                if (repo.hasRefreshToken) {
                    com.carytm.music.auth.GoogleDeviceAuthManager(requireContext()).refreshAccessToken(force = true)
                }
            }
            val list = InnertubeApi.getUserPlaylists(requireContext())
            swipeRefresh?.isRefreshing = false
            playlistList.clear()
            playlistList.addAll(list)
            playlistAdapter.submitList(playlistList)
        }
    }
}
