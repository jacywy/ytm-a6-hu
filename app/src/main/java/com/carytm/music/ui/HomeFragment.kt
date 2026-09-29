package com.carytm.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carytm.music.R
import com.carytm.music.model.SongItem
import com.carytm.music.net.InnertubeApi
import com.carytm.music.player.MusicPlayer
import com.carytm.music.ui.adapter.SongAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), MusicPlayer.PlaybackListener {

    private lateinit var rvContent: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var songAdapter: SongAdapter
    private val scope = CoroutineScope(Dispatchers.Main)
    private val songList = mutableListOf<SongItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        rvContent = view.findViewById(R.id.rv_home_content)
        loading = view.findViewById(R.id.home_loading)

        rvContent.layoutManager = LinearLayoutManager(context)
        songAdapter = SongAdapter { _, index ->
            MusicPlayer.playQueue(songList, index)
        }
        rvContent.adapter = songAdapter

        MusicPlayer.addListener(this)
        songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)

        loadHomeData()
        return view
    }

    private fun loadHomeData() {
        loading.visibility = View.VISIBLE
        scope.launch {
            val (_, songs) = InnertubeApi.getHomeRecommendations()
            loading.visibility = View.GONE
            songList.clear()
            songList.addAll(songs)
            songAdapter.submitList(songList)
            scrollToCurrentPlaying()
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
