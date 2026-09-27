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
                            accountRepo.refreshToken = tokenResponse.refreshToken
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

    fun cancelPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
