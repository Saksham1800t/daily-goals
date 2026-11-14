package com.example.dailygoals.blocker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.dailygoals.R
import com.example.dailygoals.engine.EmergencyPause
import com.example.dailygoals.engine.PauseLength
import com.example.dailygoals.engine.TempAccessOffer

private const val BACKGROUND = 0xFF0C0A14.toInt()
private const val TEXT = 0xFFF6F4FF.toInt()
private const val TEXT_SECONDARY = 0xFFABA6C2.toInt()
private const val LIME = 0xFFC6F432.toInt()
private const val MINT = 0xFF2EE6A6.toInt()
private const val DARK_ON_ACCENT = 0xFF0C1405.toInt()
private const val OUTLINE = 0xFF3A3552.toInt()

class BlockViewActions(
    val onGoHome: () -> Unit,
    val onStopTest: () -> Unit,
    val onPause: (PauseLength) -> Unit,
    val onTempAccess: () -> Unit,
)

/**
 * The block interface, built from plain views so the same layout works inside an accessibility overlay
 * window, where there is no activity and no Compose host.
 */
fun buildBlockView(context: Context, model: BlockScreenModel, actions: BlockViewActions): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val handler = Handler(Looper.getMainLooper())

    fun label(text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(context).apply {
        this.text = text
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(top), 0, 0)
    }

    fun button(text: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (filled) DARK_ON_ACCENT else TEXT_SECONDARY)
        if (filled) background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(LIME, MINT)).apply { cornerRadius = dp(28).toFloat() }
        setPadding(dp(32), dp(14), dp(32), dp(14))
        isClickable = true
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun holdButton(text: String, length: PauseLength) = TextView(context).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(TEXT_SECONDARY)
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), OUTLINE)
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        var seconds = EmergencyPause.HOLD_MILLIS / 1000
        val tick = object : Runnable {
            override fun run() {
                seconds -= 1
                if (seconds <= 0) {
                    actions.onPause(length)
                    return
                }
                this@apply.text = context.getString(R.string.block_pause_holding, seconds.toInt())
                handler.postDelayed(this, 1000)
            }
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    seconds = EmergencyPause.HOLD_MILLIS / 1000
                    this.text = context.getString(R.string.block_pause_holding, seconds.toInt())
                    handler.postDelayed(tick, 1000)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(tick)
                    this.text = text
                    view.performClick()
                    true
                }
                else -> true
            }
        }
    }

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(BACKGROUND)
        setPadding(dp(32), dp(32), dp(32), dp(32))
        isClickable = true
        isFocusable = false
    }
    model.icon?.let { icon ->
        root.addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { bottomMargin = dp(12) })
    }
    root.addView(label(model.appName, 20f, TEXT_SECONDARY, bold = true))
    root.addView(label(model.message, 26f, TEXT, bold = true, top = 16))
    root.addView(label(model.countdown, 16f, TEXT_SECONDARY, top = 12))
    model.attemptsLine?.let { root.addView(label(it, 14f, TEXT_SECONDARY, top = 4)) }
    model.alsoBlockedBy.forEach { root.addView(label(it, 14f, TEXT_SECONDARY, top = 6)) }
    root.addView(
        button(context.getString(R.string.poc_block_go_home), filled = true, onClick = actions.onGoHome),
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32) },
    )
    if (model.isTest) {
        root.addView(button(context.getString(R.string.poc_block_stop), filled = false, onClick = actions.onStopTest))
    }
    when (model.tempAccess) {
        TempAccessOffer.AVAILABLE -> root.addView(
            button(context.getString(R.string.temp_button), filled = false, onClick = actions.onTempAccess),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        TempAccessOffer.NONE_LEFT -> root.addView(label(context.getString(R.string.temp_none_left), 12f, TEXT_SECONDARY, top = 16))
        TempAccessOffer.GOAL_FORBIDS ->
            root.addView(label(context.getString(R.string.temp_goal_forbids, model.tempForbiddenBy.orEmpty()), 12f, TEXT_SECONDARY, top = 16))
        TempAccessOffer.OFF -> root.addView(label(context.getString(R.string.temp_off), 12f, TEXT_SECONDARY, top = 16))
    }
    if (model.pauseAvailable) {
        root.addView(label(context.getString(R.string.block_pause_hint), 12f, TEXT_SECONDARY, top = 28))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(
            R.string.block_pause_30 to PauseLength.THIRTY_MINUTES,
            R.string.block_pause_60 to PauseLength.ONE_HOUR,
            R.string.block_pause_day to PauseLength.END_OF_TODAY,
        ).forEach { (text, length) ->
            row.addView(
                holdButton(context.getString(text), length),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4); marginEnd = dp(4); topMargin = dp(8) },
            )
        }
        root.addView(row)
    } else {
        root.addView(label(context.getString(R.string.block_pause_used_up), 12f, TEXT_SECONDARY, top = 28))
    }
    return root
}
