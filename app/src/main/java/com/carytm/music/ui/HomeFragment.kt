package com.carytm.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.carytm.music.R
import com.carytm.music.model.SongItem
import com.carytm.music.net.InnertubeApi
import com.carytm.music.player.MusicPlayer
import com.carytm.music.ui.adapter.SongAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), MusicPlayer.PlaybackListener {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvContent: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var songAdapter: SongAdapter
    private val scope = CoroutineScope(Dispatchers.Main)
    private val songList = mutableListOf<SongItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home)
        rvContent = view.findViewById(R.id.rv_home_content)
        loading = view.findViewById(R.id.home_loading)

        swipeRefresh.setColorSchemeResources(R.color.primary)
        swipeRefresh.setOnRefreshListener {
            loadHomeData(isSwipe = true)
        }

        rvContent.layoutManager = LinearLayoutManager(context)
        songAdapter = SongAdapter { _, index ->
            MusicPlayer.playQueue(songList, index)
        }
        rvContent.adapter = songAdapter

        MusicPlayer.addListener(this)
        songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)

        if (songList.isEmpty()) {
            loadHomeData(isSwipe = false)
        } else {
            songAdapter.submitList(songList)
            scrollToCurrentPlaying()
        }

        return view
    }

    private fun loadHomeData(isSwipe: Boolean = false) {
        if (!isSwipe && songList.isEmpty()) {
            loading.visibility = View.VISIBLE
        }
        scope.launch {
            val (_, songs) = InnertubeApi.getHomeRecommendations()
            loading.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            if (songs.isNotEmpty()) {
                songList.clear()
                songList.addAll(songs)
                songAdapter.submitList(songList)
                scrollToCurrentPlaying()
            }
        }
    }

    private fun scrollToCurrentPlaying() {
        val currentVideoId = MusicPlayer.getCurrentSong()?.videoId ?: return
        val index = songList.indexOfFirst { it.videoId == currentVideoId }
        if (index >= 0) {
            val lm = rvContent.layoutManager as? LinearLayoutManager ?: return
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
            scrollToCurrentPlaying()
        }
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {}
    override fun onBuffering(isBuffering: Boolean) {}
    override fun onProgressUpdate(currentMs: Long, totalMs: Long) {}
    override fun onError(message: String) {}

    override fun onDestroyView() {
        super.onDestroyView()
        MusicPlayer.removeListener(this)
    }
}
