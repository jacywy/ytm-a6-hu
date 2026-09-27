package com.carytm.music

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.carytm.music.extractor.StreamResolver
import com.carytm.music.net.NetworkClient
import com.carytm.music.player.MusicPlayer
import com.carytm.music.util.LocaleHelper

class CarYtmApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.onAttach(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Conscrypt TLS 1.3 Provider & Network Client
        NetworkClient.init(this)

        // 2. Initialize NewPipeExtractor stream resolver engine
        StreamResolver.init()

        // 3. Initialize ExoPlayer with disk cache
        MusicPlayer.init(this)
    }
}
