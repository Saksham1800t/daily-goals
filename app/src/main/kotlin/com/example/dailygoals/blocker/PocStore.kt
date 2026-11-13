package com.example.dailygoals.blocker

import android.content.Context
import com.example.dailygoals.engine.ActiveBlock
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.BlockSession
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** How the block is shown over the test app; the proof of concept tries them in the plan's order. */
enum class BlockMethod { ACTIVITY, OVERLAY, HOME, OVERLAY_THEN_HOME, ACTIVITY_THEN_HOME, HOME_THEN_ACTIVITY }

/**
 * State and log for the blocking test. It lives in plain preferences and a text file so the service can
 * read it after a reboot without any screen of the app having been opened.
 */
class PocStore(context: Context, private val registry: BlockRegistry) {
    private val prefs = context.getSharedPreferences("block_test", Context.MODE_PRIVATE)
    private val logFile = File(context.filesDir, "block-test.log")
    private val stamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    var target: String
        get() = prefs.getString(KEY_TARGET, DEFAULT_TARGET).orEmpty().ifBlank { DEFAULT_TARGET }
        set(value) = prefs.edit().putString(KEY_TARGET, value.trim()).apply()

    var method: BlockMethod
        get() = runCatching { BlockMethod.valueOf(prefs.getString(KEY_METHOD, null).orEmpty()) }.getOrDefault(BlockMethod.OVERLAY)
        set(value) = prefs.edit().putString(KEY_METHOD, value.name).apply()

    /** Whether to press pause on whatever is playing when the block appears, so the blocked app goes quiet too. */
    var pauseMedia: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_MEDIA, value).apply()

    /** Whether the service runs as a foreground service with an ongoing notification. */
    var keepAlive: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    /** When the watchdog last reminded the user that the service was off, or 0. */
    var lastWatchdogNotice: Long
        get() = prefs.getLong(KEY_WATCHDOG_NOTICE, 0L)
        set(value) = prefs.edit().putLong(KEY_WATCHDOG_NOTICE, value).apply()

    fun session(): BlockSession? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val until = prefs.getLong(KEY_UNTIL, NO_END)
        return BlockSession(target, until.takeIf { it != NO_END })
    }

    /** [minutes] of null blocks until the test is switched off. */
    fun start(minutes: Int?) {
        val until = minutes?.let { System.currentTimeMillis() + it * MILLIS_PER_MINUTE } ?: NO_END
        prefs.edit().putBoolean(KEY_ACTIVE, true).putLong(KEY_UNTIL, until).apply()
        val end = if (until == NO_END) System.currentTimeMillis() + OPEN_ENDED_MILLIS else until
        registry.addBlock(ActiveBlock(BlockScreens.TEST_GOAL_ID, "Blocking test", target, BlockKind.PUNISHMENT, end))
        log("test started: $target, method $method, ${minutes?.let { "$it min" } ?: "until switched off"}")
    }

    fun stop() {
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
        registry.removeGoal(BlockScreens.TEST_GOAL_ID)
        log("test stopped")
    }

    @Synchronized
    fun log(message: String) {
        runCatching {
            logFile.appendText("${LocalTime.now().format(stamp)} $message\n")
            if (logFile.length() > MAX_LOG_BYTES) {
                logFile.writeText(logFile.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    @Synchronized
    fun readLog(limit: Int = SHOWN_LINES): List<String> =
        runCatching { logFile.readLines().takeLast(limit) }.getOrDefault(emptyList())

    @Synchronized
    fun clearLog() {
        runCatching { logFile.writeText("") }
    }

    companion object {
        const val DEFAULT_TARGET = "com.android.chrome"
        private const val KEY_TARGET = "target"
        private const val KEY_METHOD = "method"
        private const val KEY_ACTIVE = "active"
        private const val KEY_PAUSE_MEDIA = "pause_media"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_WATCHDOG_NOTICE = "watchdog_notice"
        private const val KEY_UNTIL = "until"
        private const val NO_END = -1L
        private const val OPEN_ENDED_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MAX_LOG_BYTES = 60_000L
        private const val KEEP_LINES = 200
        private const val SHOWN_LINES = 60
    }
}
