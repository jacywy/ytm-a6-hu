package com.carytm.music.player

import android.content.Context
import android.content.SharedPreferences
import com.carytm.music.model.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LikedRepository {

    private const val PREFS_NAME = "carytm_liked_songs"
    private const val KEY_LIKED_IDS = "liked_video_ids"
    private const val KEY_LIKED_SONGS_JSON = "liked_songs_metadata"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun isLiked(context: Context, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val prefs = getPrefs(context)
        val ids = prefs.getStringSet(KEY_LIKED_IDS, emptySet()) ?: emptySet()
        return ids.contains(videoId)
    }

    @Synchronized
    fun setLiked(context: Context, song: SongItem, isLiked: Boolean) {
        val prefs = getPrefs(context)
        val ids = (prefs.getStringSet(KEY_LIKED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val songs = getLikedSongs(context).toMutableList()

        if (isLiked) {
            ids.add(song.videoId)
            // Put recently liked song at the top
            songs.removeAll { it.videoId == song.videoId }
            songs.add(0, song)
        } else {
            ids.remove(song.videoId)
            songs.removeAll { it.videoId == song.videoId }
        }

        val json = gson.toJson(songs)
        prefs.edit()
            .putStringSet(KEY_LIKED_IDS, ids)
            .putString(KEY_LIKED_SONGS_JSON, json)
            .apply()
    }

    @Synchronized
    fun getLikedSongs(context: Context): List<SongItem> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_LIKED_SONGS_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SongItem>>() {}.type
            gson.fromJson<List<SongItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Merge remote songs from "Liked Songs" (LM playlist) into local repository so they are
     * permanently accessible offline.
     */
    @Synchronized
    fun syncFromRemote(context: Context, remoteSongs: List<SongItem>) {
        if (remoteSongs.isEmpty()) return
        val prefs = getPrefs(context)
        val ids = (prefs.getStringSet(KEY_LIKED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val localSongs = getLikedSongs(context).toMutableList()

        for (song in remoteSongs) {
            ids.add(song.videoId)
            if (localSongs.none { it.videoId == song.videoId }) {
                localSongs.add(song)
            }
        }

        val json = gson.toJson(localSongs)
        prefs.edit()
            .putStringSet(KEY_LIKED_IDS, ids)
            .putString(KEY_LIKED_SONGS_JSON, json)
            .apply()
    }
}
