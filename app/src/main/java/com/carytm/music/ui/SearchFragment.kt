package com.carytm.music.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

class SearchFragment : Fragment(), MusicPlayer.PlaybackListener {

    private lateinit var etSearchInput: EditText
    private lateinit var btnSearchClear: View
    private lateinit var btnSearchSubmit: Button
    private lateinit var layoutSearchStatus: View
    private lateinit var searchLoading: ProgressBar
    private lateinit var tvSearchStatus: TextView
    private lateinit var btnSearchRetry: Button
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var songAdapter: SongAdapter

    private val scope = CoroutineScope(Dispatchers.Main)
    private val searchResults = mutableListOf<SongItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        etSearchInput = view.findViewById(R.id.et_search_input)
        btnSearchClear = view.findViewById(R.id.btn_search_clear)
        btnSearchSubmit = view.findViewById(R.id.btn_search_submit)
        layoutSearchStatus = view.findViewById(R.id.layout_search_status)
        searchLoading = view.findViewById(R.id.search_loading)
        tvSearchStatus = view.findViewById(R.id.tv_search_status)
        btnSearchRetry = view.findViewById(R.id.btn_search_retry)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_search)
        rvSearchResults = view.findViewById(R.id.rv_search_results)

        btnSearchRetry.setOnClickListener {
            performSearch(isSwipe = false)
        }

        swipeRefresh.setColorSchemeResources(R.color.primary)
        swipeRefresh.setOnRefreshListener {
            performSearch(isSwipe = true)
        }

        rvSearchResults.layoutManager = LinearLayoutManager(context)
        songAdapter = SongAdapter { _, index ->
            MusicPlayer.playQueue(searchResults, index)
        }
        rvSearchResults.adapter = songAdapter

        MusicPlayer.addListener(this)
        songAdapter.setCurrentPlaying(MusicPlayer.getCurrentSong()?.videoId)

        btnSearchSubmit.setOnClickListener {
            performSearch(isSwipe = false)
        }

        btnSearchClear.setOnClickListener {
            etSearchInput.text?.clear()
            layoutSearchStatus.visibility = View.GONE
            searchResults.clear()
            songAdapter.submitList(searchResults)
            etSearchInput.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }

        etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnSearchClear.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        btnSearchClear.visibility = if (etSearchInput.text.isNotEmpty()) View.VISIBLE else View.GONE

        etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(isSwipe = false)
                true
            } else false
        }

        if (searchResults.isNotEmpty()) {
            songAdapter.submitList(searchResults)
            scrollToCurrentPlaying()
        }

        return view
    }

    private fun performSearch(isSwipe: Boolean = false) {
        val query = etSearchInput.text.toString().trim()
        if (query.isBlank()) {
            swipeRefresh.isRefreshing = false
            return
        }

        // Hide keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearchInput.windowToken, 0)

        if (!isSwipe) {
            layoutSearchStatus.visibility = View.VISIBLE
            searchLoading.visibility = View.VISIBLE
            tvSearchStatus.text = getString(R.string.search_loading_hint)
            btnSearchRetry.visibility = View.GONE
        }
        scope.launch {
            try {
                val list = InnertubeApi.search(query)
                searchLoading.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                searchResults.clear()
                if (list.isNotEmpty()) {
                    layoutSearchStatus.visibility = View.GONE
                    searchResults.addAll(list)
                    songAdapter.submitList(searchResults)
                    scrollToCurrentPlaying()
                } else {
                    songAdapter.submitList(searchResults)
                    layoutSearchStatus.visibility = View.VISIBLE
                    tvSearchStatus.text = getString(R.string.search_empty)
                    btnSearchRetry.visibility = View.GONE
                }
            } catch (e: Exception) {
                searchLoading.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                searchResults.clear()
                songAdapter.submitList(searchResults)
                layoutSearchStatus.visibility = View.VISIBLE
                tvSearchStatus.text = getString(R.string.load_failed_hint)
                btnSearchRetry.visibility = View.VISIBLE
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
