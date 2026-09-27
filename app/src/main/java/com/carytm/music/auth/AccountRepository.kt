package com.carytm.music.auth

import android.content.Context
import android.content.SharedPreferences

class AccountRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("carytm_account", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var cookies: String?
        get() = prefs.getString("cookies", null)
        set(value) = prefs.edit().putString("cookies", value).apply()

    var accountName: String?
        get() = prefs.getString("account_name", null)
        set(value) = prefs.edit().putString("account_name", value).apply()

    var customClientId: String?
        get() = prefs.getString("custom_client_id", null)
        set(value) = prefs.edit().putString("custom_client_id", value).apply()

    var customClientSecret: String?
        get() = prefs.getString("custom_client_secret", null)
        set(value) = prefs.edit().putString("custom_client_secret", value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() || !cookies.isNullOrBlank()

    val hasCookies: Boolean
        get() = !cookies.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
