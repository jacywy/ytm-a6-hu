package com.carytm.music.player

import android.content.Context
import android.content.SharedPreferences
import com.carytm.music.model.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PlaybackSavedState(
    val queue: List<SongItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val isShuffle: Boolean
)

object PlaybackStateManager {

    private const val PREFS_NAME = "carytm_playback_state"
    private const val KEY_QUEUE = "saved_queue_json"
    private const val KEY_CURRENT_INDEX = "saved_current_index"
    private const val KEY_POSITION_MS = "saved_position_ms"
    private const val KEY_IS_SHUFFLE = "saved_is_shuffle"
    private const val KEY_TIMESTAMP = "saved_timestamp"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun saveState(
        context: Context,
        queue: List<SongItem>,
        currentIndex: Int,
        positionMs: Long,
        isShuffle: Boolean
    ) {
        if (queue.isEmpty()) return
        try {
            val prefs = getPrefs(context)
            val queueJson = gson.toJson(queue)
            prefs.edit()
                .putString(KEY_QUEUE, queueJson)
                .putInt(KEY_CURRENT_INDEX, currentIndex)
                .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
                .putBoolean(KEY_IS_SHUFFLE, isShuffle)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun saveProgress(context: Context, positionMs: Long) {
        try {
            val prefs = getPrefs(context)
            prefs.edit()
                .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun hasSavedState(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_QUEUE) && prefs.getInt(KEY_CURRENT_INDEX, -1) >= 0
    }

    @Synchronized
    fun restoreState(context: Context): PlaybackSavedState? {
        val prefs = getPrefs(context)
        val queueJson = prefs.getString(KEY_QUEUE, null) ?: return null
        val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, -1)
        if (currentIndex < 0) return null

        val positionMs = prefs.getLong(KEY_POSITION_MS, 0L)
        val isShuffle = prefs.getBoolean(KEY_IS_SHUFFLE, false)

        return try {
            val type = object : TypeToken<List<SongItem>>() {}.type
            val queue: List<SongItem> = gson.fromJson(queueJson, type) ?: return null
            if (queue.isEmpty() || currentIndex !in queue.indices) return null
            PlaybackSavedState(queue, currentIndex, positionMs, isShuffle)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
