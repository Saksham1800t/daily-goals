package com.example.dailygoals.blocker

import android.content.Context
import android.graphics.drawable.Drawable
import com.example.dailygoals.R
import com.example.dailygoals.engine.AppBlocks
import com.example.dailygoals.engine.BlockMessages
import com.example.dailygoals.engine.MessageValues
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the block screen shows for one blocked app, worked out from the stored blocks. */
data class BlockScreenModel(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val message: String,
    val countdown: String,
    val attemptsLine: String?,
    val alsoBlockedBy: List<String>,
    val isTest: Boolean,
    val pauseAvailable: Boolean,
)

object BlockScreens {
    const val TEST_GOAL_ID = -1L

    fun build(context: Context, registry: BlockRegistry, blocks: AppBlocks, attempts: Int, nowMillis: Long): BlockScreenModel {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(blocks.packageName, 0) }.getOrNull()
        val appName = info?.let { pm.getApplicationLabel(it).toString() } ?: blocks.packageName
        val longest = blocks.longest
        val template = BlockMessages.choose(longest.message, registry.defaultMessage(longest.kind), longest.kind)
        val message = BlockMessages.render(
            template,
            MessageValues(
                app = appName,
                goal = longest.goalName,
                until = untilText(longest.endsAtMillis, nowMillis),
                timeLeft = timeLeft(context, longest.endsAtMillis - nowMillis),
                why = null,
                attempts = attempts,
            ),
        )
        return BlockScreenModel(
            packageName = blocks.packageName,
            appName = appName,
            icon = info?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() },
            message = message,
            countdown = context.getString(R.string.block_countdown, timeLeft(context, longest.endsAtMillis - nowMillis)),
            attemptsLine = if (registry.showAttempts) context.getString(R.string.block_attempt, attempts) else null,
            alsoBlockedBy = blocks.others.map { context.getString(R.string.block_also_line, it.goalName, untilText(it.endsAtMillis, nowMillis)) },
            isTest = longest.goalId == TEST_GOAL_ID,
            pauseAvailable = registry.canStartPause(nowMillis),
        )
    }

    fun view(context: Context, registry: BlockRegistry, packageName: String?, actions: BlockViewActions): android.view.View? {
        val now = System.currentTimeMillis()
        val blocks = packageName?.let { registry.blockFor(it, now) } ?: return null
        return buildBlockView(context, build(context, registry, blocks, registry.attemptsToday(blocks.packageName, now), now), actions)
    }

    fun untilText(endMillis: Long, nowMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        val sameDay = end.toLocalDate() == Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return end.format(if (sameDay) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("EEE HH:mm"))
    }

    fun timeLeft(context: Context, leftMillis: Long): String {
        val minutes = ((leftMillis.coerceAtLeast(0) + 59_999) / 60_000).toInt()
        return if (minutes >= 60) context.getString(R.string.block_left_hours, minutes / 60, minutes % 60) else context.getString(R.string.block_left_minutes, minutes)
    }
}
