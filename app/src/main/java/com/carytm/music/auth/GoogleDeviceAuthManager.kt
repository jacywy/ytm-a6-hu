package com.carytm.music.auth

import android.content.Context
import com.carytm.music.model.DeviceCodeResponse
import com.carytm.music.model.TokenResponse
import com.carytm.music.net.NetworkClient
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.Request

class GoogleDeviceAuthManager(private val context: Context) {

    private val gson = Gson()
    private val accountRepo = AccountRepository(context)
    private var pollJob: Job? = null

    // Standard YouTube on TV / Limited Input OAuth Client ID
    // (Used broadly by SmartTube and open-source TV clients)
    companion object {
        const val CLIENT_ID = "861556708454-d6dlm3lh05dd8pvsklbp597qpo80rhuh.apps.googleusercontent.com"
        const val CLIENT_SECRET = "S_g..._secret" // YouTube on TV public client secret or omitted if public
        const val SCOPE = "https://www.googleapis.com/auth/youtube"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }

    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .build()

            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(formBody)
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (response.isSuccessful) {
                return@withContext gson.fromJson(body, DeviceCodeResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                    val formBody = FormBody.Builder()
                        .add("client_id", CLIENT_ID)
                        .add("device_code", deviceCode)
                        .add("grant_type", "http://oauth.net/grant_type/device/1.0")
                        .build()

                    val request = Request.Builder()
                        .url(TOKEN_URL)
                        .post(formBody)
                        .build()

                    val response = NetworkClient.okHttpClient.newCall(request).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val tokenResponse = gson.fromJson(body, TokenResponse::class.java)
                        if (response.isSuccessful && !tokenResponse.accessToken.isNullOrEmpty()) {
                            accountRepo.accessToken = tokenResponse.accessToken
                            accountRepo.refreshToken = tokenResponse.refreshToken
                            accountRepo.accountName = "Google 账号 (已授权)"
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
                                onError(tokenResponse.error ?: "授权失败")
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
