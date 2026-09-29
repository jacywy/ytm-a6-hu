package com.carytm.music.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
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

class SearchFragment : Fragment(), MusicPlayer.PlaybackListener {

    private lateinit var etSearchInput: EditText
    private lateinit var btnSearchSubmit: Button
    private lateinit var searchLoading: ProgressBar
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var songAdapter: SongAdapter

    private val scope = CoroutineScope(Dispatchers.Main)
    private val searchResults = mutableListOf<SongItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        etSearchInput = view.findViewById(R.id.et_search_input)
        btnSearchSubmit = view.findViewById(R.id.btn_search_submit)
        searchLoading = view.findViewById(R.id.search_loading)
        rvSearchResults = view.findViewById(R.id.rv_search_results)

        rvSearchResults.layoutManager = LinearLayoutManager(context)
        songAdapter = SongAdapter { _, index ->
            MusicPlayer.playQueue(searchResults, index)
        }
        rvSearchResults.adapter = songAdapter

        MusicPlayer.addListener(this)
        songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)

        btnSearchSubmit.setOnClickListener {
            performSearch()
        }

        etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        return view
    }

    private fun performSearch() {
        val query = etSearchInput.text.toString().trim()
        if (query.isBlank()) return

        // Hide keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearchInput.windowToken, 0)

        searchLoading.visibility = View.VISIBLE
        scope.launch {
            val list = InnertubeApi.search(query)
            searchLoading.visibility = View.GONE
            searchResults.clear()
            searchResults.addAll(list)
            songAdapter.submitList(searchResults)
            scrollToCurrentPlaying()

            if (list.isEmpty()) {
                Toast.makeText(context, getString(R.string.search_empty), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollToCurrentPlaying() {
        val currentVideoId = MusicPlayer.getCurrentSong()?.videoId ?: return
        val index = searchResults.indexOfFirst { it.videoId == currentVideoId }
        if (index >= 0) {
            val lm = rvSearchResults.layoutManager as? LinearLayoutManager ?: return
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
