package com.carytm.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.carytm.music.R
import com.carytm.music.model.SongItem

class SongAdapter(
    private val onItemClick: (SongItem, Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private val items = mutableListOf<SongItem>()
    private var currentPlayingVideoId: String? = null

    fun submitList(newItems: List<SongItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setCurrentPlaying(videoId: String?) {
        if (currentPlayingVideoId == videoId) return
        currentPlayingVideoId = videoId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.videoId == currentPlayingVideoId)
    }

    override fun getItemCount(): Int = items.size

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_song_thumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_song_title)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tv_song_subtitle)
        private val ivPlayState: ImageView = itemView.findViewById(R.id.iv_song_play_state)

        fun bind(song: SongItem, isCurrentPlaying: Boolean) {
            tvTitle.text = song.title
            val sub = if (song.durationText.isNotBlank()) "${song.artist} • ${song.durationText}" else song.artist
            tvSubtitle.text = sub

            if (isCurrentPlaying) {
                tvTitle.setTextColor(itemView.context.getColor(R.color.primary))
                ivPlayState.setImageResource(R.drawable.ic_pause)
                ivPlayState.setColorFilter(itemView.context.getColor(R.color.primary))
            } else {
                tvTitle.setTextColor(itemView.context.getColor(R.color.text_primary))
                ivPlayState.setImageResource(R.drawable.ic_play)
                ivPlayState.setColorFilter(itemView.context.getColor(R.color.text_secondary))
            }

            if (song.thumbnailUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .load(song.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(ivThumb)
            } else {
                ivThumb.setImageResource(R.drawable.ic_music_placeholder)
            }

            itemView.setOnClickListener {
                onItemClick(song, adapterPosition)
            }
        }
    }
}
