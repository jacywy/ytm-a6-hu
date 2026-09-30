package com.carytm.music.update

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.carytm.music.BuildConfig
import com.carytm.music.R
import com.carytm.music.net.NetworkClient
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String
)

object AppUpdateManager {

    private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/jacywy/ytm-a6-hu/releases/latest"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Dedicated OkHttpClient for checking and downloading updates from GitHub & CDN.
     * Android 6.0 has frozen root certificates from 2015, causing CertPathValidatorException
     * on objects.githubusercontent.com CDN redirects.
     * This client ensures seamless downloads across all Android versions.
     */
    val updateOkHttpClient: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .hostnameVerifier { _, _ -> true }

        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        builder.build()
    }

    fun checkForUpdate(
        context: Context,
        isManual: Boolean = false,
        onFound: ((UpdateInfo) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_URL)
                    .header("User-Agent", "CarYTM-App")
                    .get()
                    .build()

                val response = updateOkHttpClient.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    val rawTag = json.get("tag_name")?.asString ?: ""
                    val remoteVersion = rawTag.removePrefix("v").trim()
                    val rawChangelog = json.get("body")?.asString?.trim() ?: ""
                    var changelog = cleanChangelog(rawChangelog)
                    if (changelog.isBlank()) {
                        changelog = fetchCommitChangelog()
                    }

                    var apkUrl: String? = null
                    val assets = json.getAsJsonArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.size()) {
                            val asset = assets[i].asJsonObject
                            val name = asset.get("name")?.asString ?: ""
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.get("browser_download_url")?.asString
                                if (name.contains("debug", ignoreCase = true) || name.contains("release", ignoreCase = true)) {
                                    break
                                }
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (apkUrl != null && isNewerVersion(remoteVersion, currentVersion)) {
                        val info = UpdateInfo(
                            versionName = remoteVersion,
                            changelog = changelog,
                            downloadUrl = apkUrl
                        )
                        mainHandler.post {
                            if (onFound != null) {
                                onFound(info)
                            } else if (context is Activity && !context.isFinishing) {
                                showUpdateDialog(context, info)
                            }
                        }
                        return@launch
                    }
                }

                if (isManual) {
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.update_already_latest), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isManual) {
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun showUpdateDialog(activity: Activity, info: UpdateInfo) {
        if (activity.isFinishing) return

        val title = activity.getString(R.string.update_dialog_title, info.versionName)
        val message = if (info.changelog.isNotBlank()) info.changelog else activity.getString(R.string.update_default_changelog)

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.update_btn_download_now) { _, _ ->
                downloadAndInstallApk(activity, info)
            }
            .setNegativeButton(R.string.update_btn_later, null)
            .show()
    }

    fun downloadAndInstallApk(activity: Activity, info: UpdateInfo) {
        if (activity.isFinishing) return

        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(activity).apply {
            setTitle(activity.getString(R.string.update_downloading_title))
            setMessage(activity.getString(R.string.update_downloading_hint))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetDir = activity.getExternalFilesDir(null) ?: activity.filesDir
                val apkFile = File(targetDir, "CarYTM_update_${info.versionName}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val request = Request.Builder()
                    .url(info.downloadUrl)
                    .header("User-Agent", "CarYTM-App")
                    .get()
                    .build()

                val response = updateOkHttpClient.newCall(request).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    throw RuntimeException("Download failed with HTTP ${response.code}")
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var lastProgress = 0
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    mainHandler.post {
                                        progressDialog.progress = progress
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }

                mainHandler.post {
                    progressDialog.dismiss()
                    installApk(activity, apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    progressDialog.dismiss()
                    Toast.makeText(activity, activity.getString(R.string.update_download_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.update_install_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    fun cleanChangelog(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val lines = raw.lines()
        val cleanedLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.contains("Full Changelog", ignoreCase = true) ||
                trimmed.contains("/compare/v", ignoreCase = true) ||
                trimmed.startsWith("## What's Changed", ignoreCase = true) ||
                trimmed.startsWith("https://github.com", ignoreCase = true)
            ) {
                continue
            }
            cleanedLines.add(line.trimEnd())
        }
        return cleanedLines.joinToString("\n").trim()
    }

    private fun fetchCommitChangelog(): String {
        return try {
            val url = "https://api.github.com/repos/jacywy/ytm-a6-hu/commits?per_page=5"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CarYTM-App")
                .get()
                .build()
            val resp = updateOkHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return ""
            if (!resp.isSuccessful) return ""
            val commitsArray = JsonParser.parseString(body).asJsonArray ?: return ""
            val list = mutableListOf<String>()
            for (i in 0 until commitsArray.size()) {
                val commitObj = commitsArray[i].asJsonObject.getAsJsonObject("commit") ?: continue
                val msg = commitObj.get("message")?.asString?.trim() ?: continue
                val lines = msg.lines().map { it.trim() }.filter { it.isNotBlank() }
                for (line in lines) {
                    if (line.contains("merge", ignoreCase = true) || line.startsWith("Release v", ignoreCase = true)) {
                        continue
                    }
                    val formatted = if (line.startsWith("*") || line.startsWith("-") || line.startsWith("•")) {
                        line
                    } else {
                        "• $line"
                    }
                    if (!list.contains(formatted)) {
                        list.add(formatted)
                    }
                }
            }
            list.take(6).joinToString("\n")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").mapNotNull { it.trim().toIntOrNull() }
            val localParts = local.split(".").mapNotNull { it.trim().toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, localParts.size)

            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
