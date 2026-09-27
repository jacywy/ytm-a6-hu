package com.carytm.music

import android.app.Application
import com.carytm.music.extractor.StreamResolver
import com.carytm.music.net.NetworkClient
import com.carytm.music.player.MusicPlayer

class CarYtmApp : Application() {

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
