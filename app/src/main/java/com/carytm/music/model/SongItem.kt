package com.carytm.music.model

import java.io.Serializable

data class SongItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationText: String = "",
    val durationSec: Long = 0,
    val thumbnailUrl: String = "",
    val albumName: String = ""
) : Serializable
