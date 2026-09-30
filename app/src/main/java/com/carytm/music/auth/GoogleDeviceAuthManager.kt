package com.carytm.music.auth

import android.content.Context
import android.util.Log
import com.carytm.music.model.DeviceCodeResponse
import com.carytm.music.model.TokenResponse
import com.carytm.music.net.NetworkClient
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class GoogleDeviceAuthManager(private val context: Context) {

    private val gson = Gson()
    private val accountRepo = AccountRepository(context)
    private var pollJob: Job? = null

    // YouTube on TV / Limited Input OAuth Client ID & Secret
    companion object {
        const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
        const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube"
        const val DEVICE_CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
        const val TOKEN_URL = "https://www.youtube.com/o/oauth2/token"
    }

    private val effectiveClientId: String
        get() = accountRepo.customClientId?.takeIf { it.isNotBlank() } ?: CLIENT_ID

    private val effectiveClientSecret: String
        get() = accountRepo.customClientSecret?.takeIf { it.isNotBlank() } ?: CLIENT_SECRET

    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "client_id" to effectiveClientId,
                "scope" to SCOPE,
                "device_id" to UUID.randomUUID().toString().replace("-", "").take(16),
                "device_model" to "ytlr::"
            )
            val jsonBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(jsonBody)
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (response.isSuccessful) {
                return@withContext gson.fromJson(body, DeviceCodeResponse::class.java)
            } else {
                Log.e("CarYTM_Auth", "DeviceCode error: ${response.code} $body")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CarYTM_Auth", "requestDeviceCode exception: ${e.message}")
        }
        return@withContext null
    }

    fun startPollingToken(
        deviceCode: String,
        intervalSec: Int,
        onSuccess: (TokenResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        pollJob?.cancel()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            val interval = if (intervalSec < 3) 5000L else intervalSec * 1000L
            var attempts = 0
            val maxAttempts = 60 // 5 minutes max

            while (isActive && attempts < maxAttempts) {
                attempts++
                delay(interval)

                try {
                    val payload = mapOf(
                        "client_id" to effectiveClientId,
                        "client_secret" to effectiveClientSecret,
                        "code" to deviceCode,
                        "grant_type" to "http://oauth.net/grant_type/device/1.0"
                    )
                    val jsonBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = Request.Builder()
                        .url(TOKEN_URL)
                        .post(jsonBody)
                        .build()

                    val response = NetworkClient.okHttpClient.newCall(request).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val tokenResponse = gson.fromJson(body, TokenResponse::class.java)
                        if (response.isSuccessful && !tokenResponse.accessToken.isNullOrEmpty()) {
                            accountRepo.accessToken = tokenResponse.accessToken
                            if (!tokenResponse.refreshToken.isNullOrBlank()) {
                                accountRepo.refreshToken = tokenResponse.refreshToken
                            }
                            val expiresInSec = tokenResponse.expiresIn ?: 3600
                            accountRepo.tokenExpiry = System.currentTimeMillis() + expiresInSec * 1000L
                            accountRepo.accountName = "Google Account"
                            withContext(Dispatchers.Main) {
                                onSuccess(tokenResponse)
                            }
                            break
                        } else if (tokenResponse.error == "authorization_pending") {
                            // Still waiting for user approval on phone, continue polling
                            continue
                        } else if (tokenResponse.error == "slow_down") {
                            delay(2000L)
                            continue
                        } else {
                            withContext(Dispatchers.Main) {
                                onError(tokenResponse.error ?: "Authorization failed")
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Silently refreshes the OAuth access token using the stored refresh_token.
     * Returns true if the token is valid or successfully renewed.
     */
    suspend fun refreshAccessToken(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = accountRepo.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return@withContext false
        }

        if (!force && !accountRepo.isTokenExpired()) {
            return@withContext true
        }

        return@withContext refreshAccessTokenSync(force)
    }

    /**
     * Synchronous version for interceptors or blocking tasks
     */
    @Synchronized
    fun refreshAccessTokenSync(force: Boolean = false): Boolean {
        val refreshToken = accountRepo.refreshToken ?: return false
        if (!force && !accountRepo.isTokenExpired()) return true

        return try {
            val payload = mapOf(
                "client_id" to effectiveClientId,
                "client_secret" to effectiveClientSecret,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
            val jsonBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(jsonBody)
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return false
            if (response.isSuccessful) {
                val tokenResponse = gson.fromJson(body, TokenResponse::class.java)
                if (!tokenResponse.accessToken.isNullOrBlank()) {
                    accountRepo.accessToken = tokenResponse.accessToken
                    val expiresInSec = tokenResponse.expiresIn ?: 3600
                    accountRepo.tokenExpiry = System.currentTimeMillis() + expiresInSec * 1000L
                    if (!tokenResponse.refreshToken.isNullOrBlank()) {
                        accountRepo.refreshToken = tokenResponse.refreshToken
                    }
                    Log.d("CarYTM_Auth", "Access token successfully refreshed! Valid for ${expiresInSec}s")
                    true
                } else false
            } else {
                Log.e("CarYTM_Auth", "Token refresh failed: ${response.code} $body")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CarYTM_Auth", "Token refresh exception: ${e.message}")
            false
        }
    }

    fun cancelPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
