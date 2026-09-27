package com.carytm.music.ui

import android.app.AlertDialog
import android.content.Context
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
import com.carytm.music.util.LocaleHelper
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
    private lateinit var tvCacheSize: TextView
    private lateinit var btnSetCacheSize: Button
    private lateinit var btnClearCache: Button
    private lateinit var tvLanguage: TextView
    private lateinit var btnLanguage: Button

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
        tvCacheSize = view.findViewById(R.id.tv_cache_size)
        btnSetCacheSize = view.findViewById(R.id.btn_set_cache_size)
        btnClearCache = view.findViewById(R.id.btn_clear_cache)
        tvLanguage = view.findViewById(R.id.tv_settings_language)
        btnLanguage = view.findViewById(R.id.btn_settings_language)

        updateAccountUI()
        updateCacheUI()
        updateLanguageUI()

        btnLogin.setOnClickListener {
            val dialog = LoginDialog(requireContext()) {
                updateAccountUI()
                Toast.makeText(context, getString(R.string.login_tv_success_toast), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, getString(R.string.settings_logout_toast), Toast.LENGTH_SHORT).show()
        }

        btnSetCacheSize.setOnClickListener {
            val sizes = arrayOf(200, 500, 1024, 2048, 5120)
            val labels = arrayOf("200 MB", "500 MB", "1024 MB (1 GB)", "2048 MB (2 GB)", "5120 MB (5 GB)")
            val currentLimit = MusicPlayer.getCacheLimitMb(requireContext())
            var selectedIndex = sizes.indexOf(currentLimit)
            if (selectedIndex < 0) selectedIndex = 1

            AlertDialog.Builder(requireContext(), R.style.Theme_CarYTM_Dialog)
                .setTitle(getString(R.string.settings_cache_dialog_title))
                .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                    val chosenMb = sizes[which]
                    MusicPlayer.setCacheLimitMb(requireContext(), chosenMb)
                    updateCacheUI()
                    Toast.makeText(context, getString(R.string.settings_cache_limit_set_toast, labels[which]), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        btnClearCache.setOnClickListener {
            MusicPlayer.clearCache(requireContext())
            updateCacheUI()
            Toast.makeText(context, getString(R.string.settings_cache_cleared_toast), Toast.LENGTH_SHORT).show()
        }

        btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        rgAudioQuality.setOnCheckedChangeListener { _, checkedId ->
            MusicPlayer.preferOpus = (checkedId == R.id.rb_quality_opus)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        updateCacheUI()
        updateLanguageUI()
    }

    private fun showLanguageDialog() {
        val langCodes = arrayOf(LocaleHelper.LANG_SYSTEM, LocaleHelper.LANG_ZH, LocaleHelper.LANG_EN)
        val langLabels = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_zh),
            getString(R.string.lang_en)
        )
        val currentCode = LocaleHelper.getSavedLanguageCode(requireContext())
        var selectedIndex = langCodes.indexOf(currentCode)
        if (selectedIndex < 0) selectedIndex = 0

        AlertDialog.Builder(requireContext(), R.style.Theme_CarYTM_Dialog)
            .setTitle(getString(R.string.settings_language_title))
            .setSingleChoiceItems(langLabels, selectedIndex) { dialog, which ->
                val chosenCode = langCodes[which]
                if (chosenCode != currentCode) {
                    LocaleHelper.setLanguage(requireContext(), chosenCode)
                    Toast.makeText(context, getString(R.string.lang_changed_toast), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    requireActivity().recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun updateLanguageUI() {
        val currentCode = LocaleHelper.getSavedLanguageCode(requireContext())
        val name = when (currentCode) {
            LocaleHelper.LANG_ZH -> getString(R.string.lang_zh)
            LocaleHelper.LANG_EN -> getString(R.string.lang_en)
            else -> getString(R.string.lang_system)
        }
        tvLanguage.text = getString(R.string.lang_current_format, name)
    }

    private fun updateCacheUI() {
        context?.let { ctx ->
            val used = MusicPlayer.getUsedCacheSizeMb()
            val limit = MusicPlayer.getCacheLimitMb(ctx)
            tvCacheSize.text = getString(R.string.settings_cache_size_format, used, limit)
        }
    }

    private fun updateAccountUI() {
        val repo = AccountRepository(requireContext())
        if (repo.isLoggedIn) {
            val name = repo.accountName ?: (if (!repo.cookies.isNullOrBlank()) getString(R.string.settings_account_cookie_imported) else getString(R.string.settings_account_tv_auth))
            tvAccountStatus.text = getString(R.string.settings_account_logged_in_format, name)
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
                    repo.accountName = getString(R.string.settings_account_cookie_imported)
                    updateAccountUI()
                    Toast.makeText(context, getString(R.string.cookie_saved_toast), Toast.LENGTH_LONG).show()
                }
            }
            cookieServer?.start()

            val ip = getIpAddress() ?: "192.168.43.1"
            AlertDialog.Builder(requireContext(), R.style.Theme_CarYTM_Dialog)
                .setTitle(getString(R.string.cookie_server_title))
                .setMessage(getString(R.string.cookie_server_msg_format, ip))
                .setPositiveButton(getString(R.string.btn_confirm), null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.cookie_server_start_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
