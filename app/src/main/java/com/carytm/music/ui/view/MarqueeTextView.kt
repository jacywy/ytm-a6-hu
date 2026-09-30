package com.carytm.music.ui.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

/**
 * A dedicated, robust Marquee TextView designed specifically for Car Head Units (Android 6.0+).
 * 
 * Why ValueAnimator and AOSP TextView Marquee fail on car head units:
 * 1. Car head unit ROMs often set `Settings.Global.ANIMATOR_DURATION_SCALE = 0`, causing all
 *    ValueAnimators to finish instantly (0ms) and snap from start to end without animating!
 * 2. Native AOSP Marquee depends on view focus and window focus, which break constantly when
 *    user interacts with other views, playback progress updates, or navigation tabs.
 * 
 * This implementation uses an autonomous, timer-driven tick engine with Handler.postDelayed:
 * - 100% immune to system animator scale settings.
 * - 1.2s stationary pause at the beginning so driver can read the song title start.
 * - Smooth 1px/25ms (~40px/s) scroll until all trailing characters and extra clearance are fully displayed.
 * - 1.2s stationary pause at the end so driver can read feat/version/album info.
 * - Clean repeat loop.
 * - Completely lifecycle-aware (pauses on hide/detach, resumes on show).
 * - Fully constructor-safe (guarded against Android TextView XML inflation setText() callback).
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var isInitialized = false
    private val marqueeHandler by lazy { Handler(Looper.getMainLooper()) }
    private val extraEndPaddingPx: Int
        get() = (resources.displayMetrics.density * 36).toInt() // 36dp clearance

    private var currentScroll = 0
    private var maxScroll = 0
    private var isMarqueeRunning = false

    private enum class MarqueeState {
        IDLE,
        START_PAUSE,
        SCROLLING,
        END_PAUSE
    }

    private var currentState = MarqueeState.IDLE

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || visibility != View.VISIBLE || !isMarqueeRunning) {
                return
            }

            when (currentState) {
                MarqueeState.START_PAUSE -> {
                    currentState = MarqueeState.SCROLLING
                    marqueeHandler.postDelayed(this, 25L)
                }
                MarqueeState.SCROLLING -> {
                    currentScroll += 1
                    if (currentScroll >= maxScroll) {
                        currentScroll = maxScroll
                        scrollTo(currentScroll, 0)
                        currentState = MarqueeState.END_PAUSE
                        marqueeHandler.postDelayed(this, 1200L) // 1.2s end pause
                    } else {
                        scrollTo(currentScroll, 0)
                        marqueeHandler.postDelayed(this, 25L)
                    }
                }
                MarqueeState.END_PAUSE -> {
                    currentScroll = 0
                    scrollTo(0, 0)
                    currentState = MarqueeState.START_PAUSE
                    marqueeHandler.postDelayed(this, 1200L) // 1.2s start pause
                }
                MarqueeState.IDLE -> {
                    // Do nothing
                }
            }
        }
    }

    private val starterRunnable = Runnable {
        startMarqueeIfNeeded()
    }

    init {
        isSingleLine = true
        ellipsize = null // Disable native AOSP marquee
        setHorizontallyScrolling(true)
        isInitialized = true
        postRestartMarquee()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        postRestartMarquee()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val oldText = getText()?.toString()
        super.setText(text, type)
        // Guard against super TextView constructor calling setText() before MarqueeTextView properties are initialized
        if (isInitialized && oldText != text?.toString()) {
            postRestartMarquee()
        }
    }

    private fun postRestartMarquee() {
        if (!isInitialized) return
        stopMarquee()
        currentScroll = 0
        scrollTo(0, 0)
        marqueeHandler.removeCallbacks(starterRunnable)
        marqueeHandler.post(starterRunnable)
    }

    private fun startMarqueeIfNeeded() {
        if (!isInitialized) return
        stopMarquee()
        val textStr = text?.toString() ?: return
        if (textStr.isBlank()) return

        val textWidth = paint.measureText(textStr)
        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        if (availableWidth <= 0) return

        if (textWidth <= availableWidth) {
            // Text fits comfortably without scrolling
            currentScroll = 0
            scrollTo(0, 0)
            currentState = MarqueeState.IDLE
            return
        }

        maxScroll = (textWidth - availableWidth + extraEndPaddingPx).toInt()
        currentScroll = 0
        scrollTo(0, 0)
        isMarqueeRunning = true
        currentState = MarqueeState.START_PAUSE
        marqueeHandler.postDelayed(tickRunnable, 1200L) // 1.2s initial stationary pause
    }

    fun stopMarquee() {
        isMarqueeRunning = false
        currentState = MarqueeState.IDLE
        if (isInitialized) {
            marqueeHandler.removeCallbacks(tickRunnable)
            marqueeHandler.removeCallbacks(starterRunnable)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postRestartMarquee()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopMarquee()
        currentScroll = 0
        scrollTo(0, 0)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            postRestartMarquee()
        } else {
            stopMarquee()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            postRestartMarquee()
        } else {
            stopMarquee()
        }
    }
}
