package com.carytm.music.ui

import android.app.AlertDialog
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.carytm.music.R
import com.carytm.music.auth.AccountRepository
import com.carytm.music.auth.CookieImportServer
import com.carytm.music.player.MusicPlayer
import java.net.Inet4Address
import java.net.NetworkInterface

class SettingsFragment : Fragment() {

    private lateinit var tvAccountStatus: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnCookie: Button
    private lateinit var btnLogout: Button
    private lateinit var rgAudioQuality: RadioGroup
    private lateinit var rbM4a: RadioButton
    private lateinit var rbOpus: RadioButton
    private lateinit var btnClearCache: Button

    private var cookieServer: CookieImportServer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        tvAccountStatus = view.findViewById(R.id.tv_settings_account)
        btnLogin = view.findViewById(R.id.btn_settings_login)
        btnCookie = view.findViewById(R.id.btn_settings_cookie)
        btnLogout = view.findViewById(R.id.btn_settings_logout)
        rgAudioQuality = view.findViewById(R.id.rg_audio_quality)
        rbM4a = view.findViewById(R.id.rb_quality_m4a)
        rbOpus = view.findViewById(R.id.rb_quality_opus)
        btnClearCache = view.findViewById(R.id.btn_clear_cache)

        updateAccountUI()

        btnLogin.setOnClickListener {
            val dialog = LoginDialog(requireContext()) {
                updateAccountUI()
                Toast.makeText(context, "Google TV 授权成功！", Toast.LENGTH_SHORT).show()
            }
            dialog.show()
        }

        btnCookie.setOnClickListener {
            startCookieServerAndShowPrompt()
        }

        btnLogout.setOnClickListener {
            val repo = AccountRepository(requireContext())
            repo.clear()
            updateAccountUI()
            Toast.makeText(context, "已退出当前账号", Toast.LENGTH_SHORT).show()
        }

        btnClearCache.setOnClickListener {
            MusicPlayer.clearCache()
            Toast.makeText(context, "本地缓存已清空", Toast.LENGTH_SHORT).show()
        }

        rgAudioQuality.setOnCheckedChangeListener { _, checkedId ->
            MusicPlayer.preferOpus = (checkedId == R.id.rb_quality_opus)
        }

        return view
    }

    private fun updateAccountUI() {
        val repo = AccountRepository(requireContext())
        if (repo.isLoggedIn) {
            val name = repo.accountName ?: (if (!repo.cookies.isNullOrBlank()) "Cookie 导入账号" else "Google TV 授权账号")
            tvAccountStatus.text = "已登录：$name"
            btnLogout.visibility = View.VISIBLE
        } else {
            tvAccountStatus.text = getString(R.string.not_logged_in)
            btnLogout.visibility = View.GONE
        }
    }

    private fun startCookieServerAndShowPrompt() {
        try {
            cookieServer?.stop()
            cookieServer = CookieImportServer(8888) { newCookies ->
                activity?.runOnUiThread {
                    val repo = AccountRepository(requireContext())
                    repo.cookies = newCookies
                    repo.accountName = "Cookie 登录账号"
                    updateAccountUI()
                    Toast.makeText(context, "Cookie 导入成功并已保存！", Toast.LENGTH_LONG).show()
                }
            }
            cookieServer?.start()

            val ip = getIpAddress() ?: "192.168.43.1"
            AlertDialog.Builder(requireContext(), R.style.Theme_CarYTM_Dialog)
                .setTitle("局域网 Cookie 导入助手")
                .setMessage("请在手机连接同 Wi-Fi 或车机热点后，使用手机浏览器访问：\n\nhttp://$ip:8888\n\n在打开的网页中粘贴 Cookie 即可瞬间同步车机。")
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "启动服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        cookieServer?.stop()
        cookieServer = null
    }
}
