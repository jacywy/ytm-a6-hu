package com.carytm.music.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "car_ytm_prefs"
    private const val KEY_LANGUAGE = "app_language"

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    fun onAttach(context: Context): Context {
        val langCode = getSavedLanguageCode(context)
        return applyLanguage(context, langCode)
    }

    fun getSavedLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
        applyLanguage(context, langCode)
        applyLanguage(context.applicationContext, langCode)
    }

    fun applyLanguage(context: Context, langCode: String): Context {
        val locale = getLocaleByCode(langCode)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            val newContext = context.createConfigurationContext(config)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            newContext
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
    }

    fun getLocaleByCode(code: String): Locale {
        return when (code) {
            LANG_ZH -> Locale.SIMPLIFIED_CHINESE
            LANG_EN -> Locale.ENGLISH
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.getSystem().configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    Resources.getSystem().configuration.locale
                }
            }
        }
    }
}
