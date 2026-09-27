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
                Toast.makeText(context, "开始随机播放本地离线音乐: ${list.size} 首歌曲", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "暂无完整离线歌曲，在线听歌时会自动完整缓存", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "开始顺序播放: ${currentTracks.size} 首歌曲", Toast.LENGTH_SHORT).show()
            }
        }

        btnDetailShuffle.setOnClickListener {
            if (currentTracks.isNotEmpty()) {
                MusicPlayer.setShuffle(true)
                MusicPlayer.playQueue(currentTracks.shuffled(), 0)
                Toast.makeText(context, "开始随机播放: ${currentTracks.size} 首歌曲", Toast.LENGTH_SHORT).show()
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
        tvDetailSubtitle.text = "${playlist.author} • 加载歌曲中..."
        pbDetailLoading.visibility = View.VISIBLE
        tvDetailEmpty.visibility = View.GONE
        currentTracks.clear()
        songAdapter.submitList(emptyList())

        loadTracksJob?.cancel()
        loadTracksJob = scope.launch {
            val tracks = InnertubeApi.getPlaylistTracks(playlist.playlistId)
            pbDetailLoading.visibility = View.GONE
            if (tracks.isNotEmpty()) {
                currentTracks.clear()
                currentTracks.addAll(tracks)
                tvDetailSubtitle.text = "${playlist.author} • 共 ${tracks.size} 首歌曲"
                songAdapter.submitList(currentTracks)
                songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)
                tvDetailEmpty.visibility = View.GONE
            } else {
                tvDetailSubtitle.text = "${playlist.author} • 0 首歌曲"
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

    override fun onSongChanged(song: SongItem?) {
        activity?.runOnUiThread {
            songAdapter.setCurrentPlaying(song?.videoId)
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
            Toast.makeText(context, "登录成功，正在加载歌单...", Toast.LENGTH_SHORT).show()
            refreshData()
        }
        dialog.show()
    }

    private fun openOfflineDetail() {
        layoutPlaylistsContainer.visibility = View.GONE
        layoutPlaylistDetail.visibility = View.VISIBLE

        tvDetailTitle.text = "本地离线音乐"
        pbDetailLoading.visibility = View.GONE
        loadTracksJob?.cancel()

        val list = OfflineRepository.getFullyCachedSongs(requireContext())
        currentTracks.clear()
        if (list.isNotEmpty()) {
            currentTracks.addAll(list)
            tvDetailSubtitle.text = "共 ${list.size} 首完整歌曲 • 无网络随时播放"
            tvDetailEmpty.visibility = View.GONE
            songAdapter.submitList(currentTracks)
            songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)
        } else {
            tvDetailSubtitle.text = "0 首歌曲"
            tvDetailEmpty.visibility = View.VISIBLE
            tvDetailEmpty.text = "暂无完整离线歌曲\n在线听歌时会自动在本地完整缓存"
            songAdapter.submitList(emptyList())
        }
    }

    private fun updateOfflineCardUI() {
        context?.let { ctx ->
            val count = OfflineRepository.getFullyCachedCount(ctx)
            tvOfflineCount.text = "已完整缓存 $count 首歌曲 • 无网络可直接播放"
        }
    }

    override fun onResume() {
        super.onResume()
        updateOfflineCardUI()
    }

    fun refreshData() {
        updateOfflineCardUI()
        val repo = AccountRepository(requireContext())
        if (repo.isLoggedIn) {
            bannerLoginPrompt.visibility = View.GONE
            loadUserPlaylists()
        } else {
            bannerLoginPrompt.visibility = View.VISIBLE
            tvAccountStatus.text = getString(R.string.not_logged_in)
            playlistList.clear()
            playlistAdapter.submitList(playlistList)
        }
    }

    private fun loadUserPlaylists() {
        scope.launch {
            val list = InnertubeApi.getUserPlaylists(requireContext())
            playlistList.clear()
            playlistList.addAll(list)
            playlistAdapter.submitList(playlistList)
        }
    }
}
