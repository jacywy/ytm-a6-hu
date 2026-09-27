package com.carytm.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carytm.music.R
import com.carytm.music.auth.AccountRepository
import com.carytm.music.model.PlaylistItem
import com.carytm.music.net.InnertubeApi
import com.carytm.music.player.MusicPlayer
import com.carytm.music.ui.adapter.PlaylistAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private lateinit var bannerLoginPrompt: View
    private lateinit var tvAccountStatus: TextView
    private lateinit var btnLoginTrigger: Button
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var playlistAdapter: PlaylistAdapter

    private val scope = CoroutineScope(Dispatchers.Main)
    private val playlistList = mutableListOf<PlaylistItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        bannerLoginPrompt = view.findViewById(R.id.banner_login_prompt)
        tvAccountStatus = view.findViewById(R.id.tv_account_status)
        btnLoginTrigger = view.findViewById(R.id.btn_login_trigger)
        rvPlaylists = view.findViewById(R.id.rv_library_playlists)

        // 2-column grid for car landscape screen
        rvPlaylists.layoutManager = GridLayoutManager(context, 2)
        playlistAdapter = PlaylistAdapter { playlist ->
            loadAndPlayPlaylist(playlist)
        }
        rvPlaylists.adapter = playlistAdapter

        btnLoginTrigger.setOnClickListener {
            showLoginDialog()
        }

        refreshData()
        return view
    }

    private fun showLoginDialog() {
        val dialog = LoginDialog(requireContext()) {
            Toast.makeText(context, "登录成功，正在加载歌单...", Toast.LENGTH_SHORT).show()
            refreshData()
        }
        dialog.show()
    }

    fun refreshData() {
        val repo = AccountRepository(requireContext())
        if (repo.isLoggedIn) {
            bannerLoginPrompt.visibility = View.GONE
            loadUserPlaylists()
        } else {
            bannerLoginPrompt.visibility = View.VISIBLE
            tvAccountStatus.text = getString(R.string.not_logged_in)
            // Even when not logged in, can display empty state or cached items
            playlistList.clear()
            playlistAdapter.submitList(playlistList)
        }
    }

    private fun loadUserPlaylists() {
        scope.launch {
            val list = InnertubeApi.getUserPlaylists()
            playlistList.clear()
            playlistList.addAll(list)
            playlistAdapter.submitList(playlistList)
        }
    }

    private fun loadAndPlayPlaylist(playlist: PlaylistItem) {
        Toast.makeText(context, "正在读取: ${playlist.title} ...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val tracks = InnertubeApi.getPlaylistTracks(playlist.playlistId)
            if (tracks.isNotEmpty()) {
                MusicPlayer.playQueue(tracks, 0)
                Toast.makeText(context, "开始播放: ${tracks.size} 首歌曲", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "歌单为空或无法解析", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
