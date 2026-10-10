package com.carytm.music.ui.view

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

/**
 * Automotive-grade Marquee TextView designed for Car Head Units (Android 6.0+).
 *
 * Why traditional approaches fail on Car Head Units:
 * 1. AOSP native Marquee: Resets on every layout pass (e.g. SeekBar/time updates every second),
 *    causing premature resets, and hardcodes max scroll to line width which clips trailing words.
 * 2. ValueAnimator: Car ROMs frequently set `ANIMATOR_DURATION_SCALE = 0`, causing animators to finish
 *    instantly in 0ms without animating.
 * 3. scrollTo(x, y): View layout passes clamp and reset mScrollX, causing rapid jumping/jittering.
 *
 * This implementation uses direct Canvas drawing with real-time delta tracking:
 * - Direct Canvas translation in onDraw(): mScrollX remains 0, completely immune to parent layout passes.
 * - Time-delta driven (35dp/s): 60 FPS smooth animation, immune to animator scale and frame drops.
 * - 1.5s start pause: Driver can read title start clearly.
 * - Complete scroll coverage: Scrolls through entire text + 40dp clearance so every trailing character is visible.
 * - 1.5s end pause: Driver can read subtitles, feat, remix info comfortably.
 * - Zero CPU/battery when stationary, detached, or hidden.
 */
class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var isInitialized = false
    private val marqueeHandler by lazy { Handler(Looper.getMainLooper()) }

    // Scroll settings
    private val speedDpPerSec = 35f
    private val extraClearanceDp = 40f
    private val startPauseMs = 1500L
    private val endPauseMs = 1500L
    private val frameIntervalMs = 16L // ~60 FPS

    private var currentScrollOffset = 0f
    private var maxScrollOffset = 0f
    private var isMarqueeRunning = false
    private var lastFrameTimeMs = 0L

    private enum class MarqueeState {
        IDLE,
        START_PAUSE,
        SCROLLING,
        END_PAUSE
    }

    private var currentState = MarqueeState.IDLE

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isInitialized || !isAttachedToWindow || visibility != View.VISIBLE || !isMarqueeRunning) {
                return
            }

            val now = SystemClock.uptimeMillis()

            when (currentState) {
                MarqueeState.START_PAUSE -> {
                    // Transition to SCROLLING after startPauseMs
                    currentState = MarqueeState.SCROLLING
                    lastFrameTimeMs = now
                    marqueeHandler.postDelayed(this, frameIntervalMs)
                }

                MarqueeState.SCROLLING -> {
                    val dt = if (lastFrameTimeMs > 0L) (now - lastFrameTimeMs).coerceIn(1L, 100L) else frameIntervalMs
                    lastFrameTimeMs = now

                    val density = resources.displayMetrics.density
                    val deltaPx = (speedDpPerSec * density) * (dt / 1000f)
                    currentScrollOffset += deltaPx

                    if (currentScrollOffset >= maxScrollOffset) {
                        currentScrollOffset = maxScrollOffset
                        invalidate()
                        currentState = MarqueeState.END_PAUSE
                        marqueeHandler.postDelayed(this, endPauseMs)
                    } else {
                        invalidate()
                        marqueeHandler.postDelayed(this, frameIntervalMs)
                    }
                }

                MarqueeState.END_PAUSE -> {
                    // Reset to beginning and pause
                    currentScrollOffset = 0f
                    invalidate()
                    currentState = MarqueeState.START_PAUSE
                    marqueeHandler.postDelayed(this, startPauseMs)
                }

                MarqueeState.IDLE -> {
                    // Stopped
                }
            }
        }
    }

    init {
        isSingleLine = true
        setHorizontallyScrolling(true)
        ellipsize = null // Disable native AOSP marquee completely
        isInitialized = true
        postCheckMarquee()
    }

    override fun setEllipsize(where: TextUtils.TruncateAt?) {
        super.setEllipsize(null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        postCheckMarquee()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val oldText = getText()?.toString()
        super.setText(text, type)
        if (isInitialized && oldText != text?.toString()) {
            postCheckMarquee()
        }
    }

    private fun postCheckMarquee() {
        if (!isInitialized) return
        marqueeHandler.removeCallbacks(tickRunnable)
        marqueeHandler.post {
            checkAndStartMarquee()
        }
    }

    private fun checkAndStartMarquee() {
        if (!isInitialized || !isAttachedToWindow || visibility != View.VISIBLE) {
            stopMarquee()
            return
        }

        val textStr = text?.toString() ?: ""
        if (textStr.isBlank()) {
            stopMarquee()
            return
        }

        val textWidth = paint.measureText(textStr)
        val availableWidth = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)

        if (availableWidth <= 0 || textWidth <= availableWidth) {
            // Text fits comfortably inside view - no scrolling needed
            stopMarquee()
            return
        }

        // Text overflows: compute max scroll distance with comfortable clearance
        val density = resources.displayMetrics.density
        val extraClearancePx = extraClearanceDp * density
        maxScrollOffset = (textWidth - availableWidth) + extraClearancePx

        currentScrollOffset = 0f
        isMarqueeRunning = true
        currentState = MarqueeState.START_PAUSE
        lastFrameTimeMs = SystemClock.uptimeMillis()
        invalidate()

        marqueeHandler.removeCallbacks(tickRunnable)
        marqueeHandler.postDelayed(tickRunnable, startPauseMs)
    }

    fun stopMarquee() {
        isMarqueeRunning = false
        currentState = MarqueeState.IDLE
        currentScrollOffset = 0f
        if (isInitialized) {
            marqueeHandler.removeCallbacks(tickRunnable)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val textStr = text?.toString() ?: ""
        if (!isMarqueeRunning || textStr.isBlank()) {
            super.onDraw(canvas)
            return
        }

        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        if (availableWidth <= 0) {
            super.onDraw(canvas)
            return
        }

        val saveCount = canvas.save()

        // Clip strictly to the text viewing window between padding bounds
        canvas.clipRect(
            compoundPaddingLeft.toFloat(),
            compoundPaddingTop.toFloat(),
            (width - compoundPaddingRight).toFloat(),
            (height - compoundPaddingBottom).toFloat()
        )

        // Compute drawing coordinates
        val x = compoundPaddingLeft.toFloat() - currentScrollOffset

        // Vertical baseline calculation
        val currentBaseline = baseline
        val y = if (currentBaseline > 0) {
            currentBaseline.toFloat()
        } else {
            val fm = paint.fontMetrics
            val contentHeight = height - compoundPaddingTop - compoundPaddingBottom
            compoundPaddingTop + (contentHeight / 2f) - ((fm.ascent + fm.descent) / 2f)
        }

        // Synchronize text paint properties
        paint.color = currentTextColor

        // Hardware-accelerated direct text render
        canvas.drawText(textStr, x, y, paint)

        canvas.restoreToCount(saveCount)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postCheckMarquee()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopMarquee()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            postCheckMarquee()
        } else {
            stopMarquee()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            postCheckMarquee()
        } else {
            stopMarquee()
        }
    }
}
