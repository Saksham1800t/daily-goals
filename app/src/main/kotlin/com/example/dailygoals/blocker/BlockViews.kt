package com.example.dailygoals.blocker

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

private const val BACKGROUND = 0xFF0C0A14.toInt()
private const val TEXT = 0xFFF6F4FF.toInt()
private const val TEXT_SECONDARY = 0xFFABA6C2.toInt()
private const val LIME = 0xFFC6F432.toInt()
private const val MINT = 0xFF2EE6A6.toInt()
private const val DARK_ON_ACCENT = 0xFF0C1405.toInt()

/**
 * The block interface, built from plain views so the same layout works inside an accessibility overlay
 * window, where there is no activity and no Compose host.
 */
fun buildBlockView(
    context: Context,
    title: String,
    message: String,
    goHomeLabel: String,
    stopLabel: String,
    onGoHome: () -> Unit,
    onStop: () -> Unit,
): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (filled) DARK_ON_ACCENT else TEXT_SECONDARY)
        if (filled) {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(LIME, MINT)).apply { cornerRadius = dp(28).toFloat() }
        }
        setPadding(dp(32), dp(14), dp(32), dp(14))
        isClickable = true
        setOnClickListener { onClick() }
    }

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(BACKGROUND)
        setPadding(dp(32), dp(32), dp(32), dp(32))
        isClickable = true
        isFocusable = false
        addView(
            TextView(context).apply {
                text = title
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(TEXT)
            },
        )
        addView(
            TextView(context).apply {
                text = message
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(12), 0, dp(32))
            },
        )
        addView(button(goHomeLabel, filled = true, onClick = onGoHome))
        addView(button(stopLabel, filled = false, onClick = onStop), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }
}
