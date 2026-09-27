package com.carytm.music.player

import android.content.Context
import android.media.AudioManager

class CarAudioFocusManager(
    context: Context,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit,
    private val onDuckRequested: (Float) -> Unit
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasAudioFocus = false

    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocus(this)
        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                onDuckRequested(1.0f) // Restore full volume
                onResumeRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Navigation voice prompt is speaking! Duck music volume to 20%
                onDuckRequested(0.2f)
            }
        }
    }
}
