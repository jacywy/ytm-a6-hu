package com.carytm.music.player

import android.content.Context
import com.carytm.music.model.SongItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OfflineRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationText: String = "",
    val durationSec: Long = 0,
    val thumbnailUrl: String = "",
    val albumName: String = "",
    var isFullyCached: Boolean = false,
    var cachedBytes: Long = 0,
    var lastPlayedTimestamp: Long = System.currentTimeMillis()
) {
    fun toSongItem(): SongItem {
        return SongItem(
            videoId = videoId,
            title = title,
            artist = artist,
            durationText = durationText,
            durationSec = durationSec,
            thumbnailUrl = thumbnailUrl,
            albumName = albumName
        )
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("videoId", videoId)
        obj.put("title", title)
        obj.put("artist", artist)
        obj.put("durationText", durationText)
        obj.put("durationSec", durationSec)
        obj.put("thumbnailUrl", thumbnailUrl)
        obj.put("albumName", albumName)
        obj.put("isFullyCached", isFullyCached)
        obj.put("cachedBytes", cachedBytes)
        obj.put("lastPlayedTimestamp", lastPlayedTimestamp)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): OfflineRecord {
            return OfflineRecord(
                videoId = obj.optString("videoId"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                durationText = obj.optString("durationText"),
                durationSec = obj.optLong("durationSec"),
                thumbnailUrl = obj.optString("thumbnailUrl"),
                albumName = obj.optString("albumName"),
                isFullyCached = obj.optBoolean("isFullyCached", false),
                cachedBytes = obj.optLong("cachedBytes", 0),
                lastPlayedTimestamp = obj.optLong("lastPlayedTimestamp", System.currentTimeMillis())
            )
        }
    }
}

object OfflineRepository {
    private const val FILE_NAME = "offline_songs_meta.json"
    private val records = mutableMapOf<String, OfflineRecord>()
    private var initialized = false
    private val lock = Any()

    private fun getMetaFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val newFile = File(baseDir, FILE_NAME)
        val oldFile = File(context.filesDir, FILE_NAME)
        if (!newFile.exists() && oldFile.exists()) {
            try {
                oldFile.copyTo(newFile, overwrite = true)
                oldFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return newFile
    }

    private fun ensureInit(context: Context) {
        synchronized(lock) {
            if (initialized) return
            try {
                val file = getMetaFile(context)
                if (file.exists()) {
                    val content = file.readText()
                    val array = JSONArray(content)
                    for (i in 0 until array.length()) {
                        val record = OfflineRecord.fromJson(array.getJSONObject(i))
                        records[record.videoId] = record
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            initialized = true
        }
    }

    private fun persist(context: Context) {
        synchronized(lock) {
            try {
                val array = JSONArray()
                records.values.forEach { array.put(it.toJson()) }
                val file = getMetaFile(context)
                file.writeText(array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isFullyCached(context: Context, videoId: String): Boolean {
        ensureInit(context)
        synchronized(lock) {
            return records[videoId]?.isFullyCached == true
        }
    }

    fun markSongStarted(context: Context, song: SongItem) {
        ensureInit(context)
        synchronized(lock) {
            val record = records[song.videoId] ?: OfflineRecord(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationText = song.durationText,
                durationSec = song.durationSec,
                thumbnailUrl = song.thumbnailUrl,
                albumName = song.albumName
            )
            record.lastPlayedTimestamp = System.currentTimeMillis()
            records[song.videoId] = record
            persist(context)
        }
    }

    fun markSongFullyCached(context: Context, song: SongItem, totalBytes: Long = 0) {
        ensureInit(context)
        synchronized(lock) {
            val record = records[song.videoId] ?: OfflineRecord(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationText = song.durationText,
                durationSec = song.durationSec,
                thumbnailUrl = song.thumbnailUrl,
                albumName = song.albumName
            )
            record.isFullyCached = true
            if (totalBytes > 0) record.cachedBytes = totalBytes
            record.lastPlayedTimestamp = System.currentTimeMillis()
            records[song.videoId] = record
            persist(context)
        }
    }

    fun markSongIncomplete(context: Context, song: SongItem) {
        ensureInit(context)
        synchronized(lock) {
            val record = records[song.videoId]
            if (record != null && !record.isFullyCached) {
                record.isFullyCached = false
                persist(context)
            }
        }
    }

    fun getFullyCachedSongs(context: Context): List<SongItem> {
        ensureInit(context)
        synchronized(lock) {
            return records.values
                .filter { it.isFullyCached }
                .sortedByDescending { it.lastPlayedTimestamp }
                .map { it.toSongItem() }
        }
    }

    fun getFullyCachedCount(context: Context): Int {
        ensureInit(context)
        synchronized(lock) {
            return records.values.count { it.isFullyCached }
        }
    }

    fun getIncompleteVideoIds(context: Context): List<String> {
        ensureInit(context)
        synchronized(lock) {
            return records.values.filter { !it.isFullyCached }.map { it.videoId }
        }
    }

    fun getAllTrackedVideoIds(context: Context): List<String> {
        ensureInit(context)
        synchronized(lock) {
            return records.keys.toList()
        }
    }

    fun getOldestFullyCachedRecords(context: Context): List<OfflineRecord> {
        ensureInit(context)
        synchronized(lock) {
            return records.values
                .filter { it.isFullyCached }
                .sortedBy { it.lastPlayedTimestamp }
        }
    }

    fun removeRecord(context: Context, videoId: String) {
        ensureInit(context)
        synchronized(lock) {
            records.remove(videoId)
            persist(context)
        }
    }

    fun clearAll(context: Context) {
        ensureInit(context)
        synchronized(lock) {
            records.clear()
            persist(context)
        }
    }
}
