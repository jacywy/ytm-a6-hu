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
    private lateinit var accountRepo: AccountRepository

    fun init(context: Context) {
        if (!initialized) {
            // Install Conscrypt Security Provider to support TLS 1.3 & updated Google Root CAs on Android 6
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            accountRepo = AccountRepository(context.applicationContext)
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

    private class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original: Request = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

            // Inject Authorization Token if available
            accountRepo.accessToken?.let { token ->
                if (!original.headers.names().contains("Authorization")) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            // Inject Cookies if available
            accountRepo.cookies?.let { cookies ->
                if (!original.headers.names().contains("Cookie")) {
                    requestBuilder.header("Cookie", cookies)
                }
            }

            return chain.proceed(requestBuilder.build())
        }
    }
}
