package com.carytm.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.carytm.music.R
import com.carytm.music.model.PlaylistItem

class PlaylistAdapter(
    private val onItemClick: (PlaylistItem) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private val items = mutableListOf<PlaylistItem>()

    fun submitList(newItems: List<PlaylistItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_card, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.iv_playlist_cover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_playlist_title)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_playlist_author)

        fun bind(item: PlaylistItem) {
            tvTitle.text = item.title
            val authorText = if (item.songCountText.isNotBlank()) "${item.author} • ${item.songCountText}" else item.author
            tvAuthor.text = authorText

            if (item.thumbnailUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .load(item.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(ivCover)
            } else {
                ivCover.setImageResource(R.drawable.ic_music_placeholder)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
