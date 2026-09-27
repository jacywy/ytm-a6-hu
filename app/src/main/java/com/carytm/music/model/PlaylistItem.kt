package com.carytm.music.model

import java.io.Serializable

data class PlaylistItem(
    val playlistId: String,
    val title: String,
    val author: String = "YouTube Music",
    val thumbnailUrl: String = "",
    val songCountText: String = ""
) : Serializable
