package dev.qokhelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/** A draggable floating button that asks for an answer when tapped, plus a panel that shows it. */
class OverlayController(private val context: Context, private val onAsk: () -> Unit) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val bubble = TextView(context)
    private val panel = TextView(context)
    private val bubbleParams = overlayParams(Gravity.TOP or Gravity.START).apply { x = dp(8); y = dp(240) }
    private val panelParams = overlayParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        y = dp(36)
    }
    private var panelShown = false

    fun show() {
        bubble.apply {
            text = "?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(IDLE_COLOR) }
            elevation = dp(6).toFloat()
        }
        bubbleParams.width = dp(56)
        bubbleParams.height = dp(56)
        attachDragAndTap()

        panel.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xE6202124.toInt()) }
            setOnClickListener { hidePanel() }
        }
        wm.addView(bubble, bubbleParams)
    }

    fun showLoading() {
        hidePanel()
        bubble.text = "…"
        (bubble.background as GradientDrawable).setColor(BUSY_COLOR)
    }

    fun showAnswer(answer: Answer) {
        bubble.text = "?"
        (bubble.background as GradientDrawable).setColor(IDLE_COLOR)
        panel.text = when (answer) {
            is Answer.Found -> buildString {
                append("گزینه ${answer.position}\n${answer.text}")
                answer.confidence?.let { append("\n$it%") }
            }
            Answer.NoQuestion -> "No question on screen"
            is Answer.Unparsed -> answer.raw
            is Answer.Failed -> answer.message
        }
        if (!panelShown) {
            wm.addView(panel, panelParams)
            panelShown = true
        }
    }

    fun hidePanel() {
        if (panelShown) {
            wm.removeView(panel)
            panelShown = false
        }
    }

    fun remove() {
        hidePanel()
        if (bubble.isAttachedToWindow) wm.removeView(bubble)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndTap() {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        bubble.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = bubbleParams.x; startY = bubbleParams.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        bubbleParams.x = startX + dx.toInt()
                        bubbleParams.y = startY + dy.toInt()
                        wm.updateViewLayout(bubble, bubbleParams)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) onAsk()
            }
            true
        }
    }

    private fun overlayParams(g: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = g }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val IDLE_COLOR = 0xCC6A1B9A.toInt()
        const val BUSY_COLOR = 0xCCF57C00.toInt()
    }
}
