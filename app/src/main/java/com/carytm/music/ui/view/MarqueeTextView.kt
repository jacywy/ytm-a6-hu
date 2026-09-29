package com.carytm.music.ui.view

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A robust Marquee TextView designed for car head units (Android 6.0+).
 * 
 * Standard Android TextView cancels or resets marquee when:
 * 1. Focus changes (touching buttons or lists)
 * 2. Window focus changes
 * 3. Identical text is set repeatedly via updates
 *
 * MarqueeTextView ensures continuous, smooth scrolling without premature resets.
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1 // marquee_forever
        isSingleLine = true
        isSelected = true
        isFocusable = true
        isFocusableInTouchMode = true
        setHorizontallyScrolling(true)
    }

    override fun isFocused(): Boolean = true

    override fun isSelected(): Boolean = true

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (focused) {
            super.onFocusChanged(true, direction, previouslyFocusedRect)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (hasWindowFocus) {
            super.onWindowFocusChanged(true)
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (getText()?.toString() == text?.toString()) {
            return
        }
        super.setText(text, type)
        isSelected = true
    }
}
