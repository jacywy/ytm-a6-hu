package com.carytm.music.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView

/**
 * A dedicated, robust Marquee TextView designed specifically for Car Head Units (Android 6.0+).
 * 
 * Traditional Android TextView marquee has severe limitations on Car Head Unit ROMs:
 * 1. Clamps or clips line width on low-resolution / narrow screens.
 * 2. Resets prematurely before the end of the text is fully shown.
 * 3. Fragile to layout changes, progress updates, and focus shifts.
 * 
 * This implementation uses a self-managed smooth scroll controller:
 * - 1.5s initial stationary pause so driver can read the beginning of the title.
 * - Smooth scroll across the full width until the tail character is fully visible (+ extra clearance).
 * - 1.5s stationary pause at the end so driver can read the subtitle / feat / version.
 * - Smooth repeat loop.
 * - Completely immune to ROM-specific TextView.Marquee glitches and focus changes.
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var scrollAnimator: ValueAnimator? = null
    private val extraEndPaddingPx: Int = (resources.displayMetrics.density * 36).toInt() // 36dp extra clearance

    private val marqueeStarter = Runnable {
        startMarqueeIfNeeded()
    }

    init {
        isSingleLine = true
        ellipsize = null // Disable native buggy AOSP marquee
        setHorizontallyScrolling(true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        postRestartMarquee()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val oldText = getText()?.toString()
        super.setText(text, type)
        if (oldText != text?.toString()) {
            postRestartMarquee()
        }
    }

    private fun postRestartMarquee() {
        removeCallbacks(marqueeStarter)
        stopMarquee()
        scrollTo(0, 0)
        post(marqueeStarter)
    }

    private fun startMarqueeIfNeeded() {
        stopMarquee()
        val textStr = text?.toString() ?: return
        if (textStr.isBlank()) return

        val textWidth = paint.measureText(textStr)
        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        if (availableWidth <= 0) return

        if (textWidth <= availableWidth) {
            // Text fits comfortably on screen, no scrolling needed
            scrollTo(0, 0)
            return
        }

        // Scroll distance guarantees every last character completely scrolls into view with extra clearance
        val totalScrollDist = (textWidth - availableWidth + extraEndPaddingPx).toInt()
        val scrollSpeedDpPerSec = 35f
        val density = resources.displayMetrics.density
        val scrollDurationMs = ((totalScrollDist / (scrollSpeedDpPerSec * density)) * 1000L).toLong().coerceAtLeast(1500L)

        val animator = ValueAnimator.ofInt(0, totalScrollDist).apply {
            duration = scrollDurationMs
            startDelay = 1500L // 1.5s initial pause so driver can read title start
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val curr = va.animatedValue as Int
                scrollTo(curr, 0)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAttachedToWindow && visibility == View.VISIBLE) {
                        // Pause at the end for 1.5s, then restart from beginning
                        postDelayed({
                            if (isAttachedToWindow && visibility == View.VISIBLE) {
                                scrollTo(0, 0)
                                startMarqueeIfNeeded()
                            }
                        }, 1500L)
                    }
                }
            })
        }

        scrollAnimator = animator
        animator.start()
    }

    fun stopMarquee() {
        scrollAnimator?.removeAllListeners()
        scrollAnimator?.removeAllUpdateListeners()
        scrollAnimator?.cancel()
        scrollAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postRestartMarquee()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(marqueeStarter)
        stopMarquee()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            postRestartMarquee()
        } else {
            removeCallbacks(marqueeStarter)
            stopMarquee()
        }
    }
}
