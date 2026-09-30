package com.carytm.music.net

import android.content.Context
import com.carytm.music.auth.AccountRepository
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object NetworkClient {

    private var initialized = false
    lateinit var accountRepo: AccountRepository
    lateinit var deviceAuthManager: com.carytm.music.auth.GoogleDeviceAuthManager
    var appContext: Context? = null

    fun init(context: Context) {
        if (!initialized) {
            val appCtx = context.applicationContext
            appContext = appCtx
            // Install Conscrypt Security Provider to support TLS 1.3 & updated Google Root CAs on Android 6
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            accountRepo = AccountRepository(appCtx)
            deviceAuthManager = com.carytm.music.auth.GoogleDeviceAuthManager(appCtx)
            initialized = true
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HeaderInterceptor())

        try {
            val tm = Conscrypt.getDefaultX509TrustManager()
            val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
            sslContext.init(null, arrayOf(tm), null)
            builder.sslSocketFactory(Conscrypt.newProvider().let { sslContext.socketFactory }, tm)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        builder.build()
    }

    /**
     * Dedicated clean client for NewPipeExtractor so internal client simulation (VisionOS, Android, etc.)
     * and headers/cookies are not modified or overridden.
     */
    val extractorOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        try {
            val tm = Conscrypt.getDefaultX509TrustManager()
            val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
            sslContext.init(null, arrayOf(tm), null)
            builder.sslSocketFactory(Conscrypt.newProvider().let { sslContext.socketFactory }, tm)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        builder.build()
    }

    /**
     * Dedicated clean client for ExoPlayer audio playback streaming directly from Googlevideo CDN.
     */
    val mediaOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        try {
            val tm = Conscrypt.getDefaultX509TrustManager()
            val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
            sslContext.init(null, arrayOf(tm), null)
            builder.sslSocketFactory(Conscrypt.newProvider().let { sslContext.socketFactory }, tm)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        builder.build()
    }

    private class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original: Request = chain.request()
            val requestBuilder = original.newBuilder()

            // Only inject default User-Agent if caller did not provide one
            if (original.header("User-Agent").isNullOrBlank()) {
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            }
            if (original.header("Accept-Language").isNullOrBlank()) {
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }

            val isOAuthRequested = original.header("X-Use-OAuth") == "true"
            if (isOAuthRequested) {
                requestBuilder.removeHeader("X-Use-OAuth")

                // Auto-refresh token if expired or about to expire in 5 minutes
                if (::deviceAuthManager.isInitialized && accountRepo.hasRefreshToken && accountRepo.isTokenExpired()) {
                    deviceAuthManager.refreshAccessTokenSync()
                }

                accountRepo.accessToken?.let { token ->
                    if (!original.headers.names().contains("Authorization")) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }
            }

            // Inject Cookies if available and not explicitly provided
            accountRepo.cookies?.let { cookies ->
                if (!original.headers.names().contains("Cookie")) {
                    requestBuilder.header("Cookie", cookies)
                }
            }

            val response = chain.proceed(requestBuilder.build())

            // Self-healing: If 401 Unauthorized occurs on OAuth request, force-refresh token and retry once
            if (response.code == 401 && isOAuthRequested && ::deviceAuthManager.isInitialized && accountRepo.hasRefreshToken) {
                response.close()
                val refreshed = deviceAuthManager.refreshAccessTokenSync(force = true)
                if (refreshed) {
                    val retryBuilder = original.newBuilder()
                    retryBuilder.removeHeader("X-Use-OAuth")
                    accountRepo.accessToken?.let { newToken ->
                        retryBuilder.header("Authorization", "Bearer $newToken")
                    }
                    return chain.proceed(retryBuilder.build())
                }
            }

            return response
        }
    }
}
