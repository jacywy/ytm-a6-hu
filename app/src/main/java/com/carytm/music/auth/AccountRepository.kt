package com.carytm.music.auth

import android.content.Context
import android.content.SharedPreferences
import com.carytm.music.player.MusicPlayer

class AccountRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("carytm_account", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) {
            prefs.edit().putString("access_token", value).apply()
            MusicPlayer.clearUrlCache()
        }

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var cookies: String?
        get() = prefs.getString("cookies", null)
        set(value) {
            prefs.edit().putString("cookies", value).apply()
            MusicPlayer.clearUrlCache()
        }

    var accountName: String?
        get() = prefs.getString("account_name", null)
        set(value) = prefs.edit().putString("account_name", value).apply()

    var customClientId: String?
        get() = prefs.getString("custom_client_id", null)
        set(value) = prefs.edit().putString("custom_client_id", value).apply()

    var customClientSecret: String?
        get() = prefs.getString("custom_client_secret", null)
        set(value) = prefs.edit().putString("custom_client_secret", value).apply()

    var tokenExpiry: Long
        get() = prefs.getLong("token_expiry", 0L)
        set(value) = prefs.edit().putLong("token_expiry", value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() || !cookies.isNullOrBlank()

    val hasCookies: Boolean
        get() = !cookies.isNullOrBlank()

    val hasRefreshToken: Boolean
        get() = !refreshToken.isNullOrBlank()

    /**
     * Check if the access token is expired or about to expire in bufferMs (default 5 minutes).
     */
    fun isTokenExpired(bufferMs: Long = 300_000L): Boolean {
        if (accessToken.isNullOrBlank()) return true
        if (tokenExpiry <= 0L) return false
        return System.currentTimeMillis() >= (tokenExpiry - bufferMs)
    }

    fun clear() {
        prefs.edit().clear().apply()
        MusicPlayer.clearUrlCache()
    }
}
