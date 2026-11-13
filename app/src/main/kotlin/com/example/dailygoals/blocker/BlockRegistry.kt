package com.example.dailygoals.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Telephony
import android.telecom.TelecomManager
import com.example.dailygoals.engine.ActiveBlock
import com.example.dailygoals.engine.AppBlocks
import com.example.dailygoals.engine.BlockCodec
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.BlockMessages
import com.example.dailygoals.engine.BlockSet
import com.example.dailygoals.engine.EmergencyPause
import com.example.dailygoals.engine.PauseLength
import com.example.dailygoals.engine.PauseUse
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the blocker enforces: the blocks of each goal, the safe apps, the emergency pause and the block
 * messages. Everything is stored in plain preferences so the service has it after a reboot with no screen
 * of the app opened. The rules themselves live in the engine package; this class only stores and asks.
 */
class BlockRegistry(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_registry", Context.MODE_PRIVATE)
    private val changeCounter = MutableStateFlow(0)

    /** Changes every time anything stored here changes, so screens know to read again. */
    val changes: StateFlow<Int> = changeCounter.asStateFlow()

    private fun touch() {
        changeCounter.value += 1
    }

    var blocks: List<ActiveBlock>
        get() = BlockCodec.decodeBlocks(prefs.getString(KEY_BLOCKS, "").orEmpty())
        private set(value) {
            prefs.edit().putString(KEY_BLOCKS, BlockCodec.encodeBlocks(value)).apply()
            touch()
        }

    fun addBlock(block: ActiveBlock) {
        blocks = blocks.filterNot { it.goalId == block.goalId && it.packageName == block.packageName } + block
    }

    fun removeGoal(goalId: Long) {
        blocks = BlockSet.withoutGoal(blocks, goalId)
    }

    fun hasActiveBlocks(nowMillis: Long = System.currentTimeMillis()): Boolean = blocks.any { nowMillis < it.endsAtMillis }

    fun dropExpired(nowMillis: Long = System.currentTimeMillis()) {
        val current = blocks
        val kept = BlockSet.withoutExpired(current, nowMillis)
        if (kept.size != current.size) blocks = kept
    }

    /** Defaults are worked out once from the installed apps, then belong to the user to edit. */
    var safeApps: Set<String>
        get() = prefs.getString(KEY_SAFE, null)?.let(BlockCodec::decodePackages) ?: detectDefaultSafeApps().also { safeApps = it }
        set(value) {
            prefs.edit().putString(KEY_SAFE, BlockCodec.encodePackages(value)).apply()
            touch()
        }

    val pauseUses: List<PauseUse>
        get() = BlockCodec.decodeUses(prefs.getString(KEY_PAUSES, "").orEmpty())

    fun pauseActive(nowMillis: Long = System.currentTimeMillis()): Boolean = EmergencyPause.isActive(pauseUses, nowMillis)

    fun canStartPause(nowMillis: Long = System.currentTimeMillis()): Boolean =
        EmergencyPause.canStart(pauseUses, nowMillis, ZoneId.systemDefault())

    fun startPause(length: PauseLength, nowMillis: Long = System.currentTimeMillis()): PauseUse? {
        val zone = ZoneId.systemDefault()
        val current = pauseUses
        if (!EmergencyPause.canStart(current, nowMillis, zone)) return null
        val next = EmergencyPause.trimmed(EmergencyPause.start(current, length, nowMillis, zone), nowMillis)
        prefs.edit().putString(KEY_PAUSES, BlockCodec.encodeUses(next)).apply()
        touch()
        return next.last()
    }

    var scheduledMessage: String
        get() = prefs.getString(KEY_SCHEDULED_MESSAGE, null) ?: BlockMessages.DEFAULT_SCHEDULED
        set(value) = putText(KEY_SCHEDULED_MESSAGE, value)

    var punishmentMessage: String
        get() = prefs.getString(KEY_PUNISHMENT_MESSAGE, null) ?: BlockMessages.DEFAULT_PUNISHMENT
        set(value) = putText(KEY_PUNISHMENT_MESSAGE, value)

    var showAttempts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ATTEMPTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_ATTEMPTS, value).apply()
            touch()
        }

    /** Counts how many times today the user tried to open a blocked app; an attempt is never a failure. */
    fun recordAttempt(packageName: String, nowMillis: Long = System.currentTimeMillis()): Int {
        val key = attemptKey(packageName, nowMillis)
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        return count
    }

    fun attemptsToday(packageName: String, nowMillis: Long = System.currentTimeMillis()): Int = prefs.getInt(attemptKey(packageName, nowMillis), 0)

    private fun attemptKey(packageName: String, nowMillis: Long) =
        "attempts:${Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()}:$packageName"

    fun defaultMessage(kind: BlockKind): String = if (kind == BlockKind.SCHEDULED) scheduledMessage else punishmentMessage

    private fun putText(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        touch()
    }

    fun blockFor(packageName: String, nowMillis: Long = System.currentTimeMillis()): AppBlocks? =
        BlockSet.blockFor(packageName, blocks, safeApps, context.packageName, pauseActive(nowMillis), nowMillis)

    fun conflicts(nowMillis: Long = System.currentTimeMillis()): List<AppBlocks> =
        BlockSet.conflicts(blocks, safeApps, context.packageName, nowMillis)

    /** Every package the service should treat as blocked right now, or none while the pause runs. */
    fun blockedPackages(nowMillis: Long = System.currentTimeMillis()): Set<String> =
        if (pauseActive(nowMillis)) emptySet() else conflicts(nowMillis).map { it.packageName }.toSet()

    private fun detectDefaultSafeApps(): Set<String> {
        val pm = context.packageManager
        val found = mutableSetOf<String>()
        fun resolve(intent: Intent) {
            runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
                .getOrNull()?.takeIf { it != "android" }?.let(found::add)
        }
        runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let(found::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(found::add)
        resolve(Intent(Intent.ACTION_DIAL))
        resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")))
        resolve(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))
        AUTHENTICATORS.filter { isInstalled(pm, it) }.forEach(found::add)
        return found - context.packageName
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess

    companion object {
        private const val KEY_BLOCKS = "blocks"
        private const val KEY_SAFE = "safe_apps"
        private const val KEY_PAUSES = "pause_uses"
        private const val KEY_SCHEDULED_MESSAGE = "scheduled_message"
        private const val KEY_PUNISHMENT_MESSAGE = "punishment_message"
        private const val KEY_SHOW_ATTEMPTS = "show_attempts"

        private val AUTHENTICATORS = listOf(
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "org.fedorahosted.freeotp",
            "com.beemdevelopment.aegis",
            "com.twofasapp",
            "org.shadowice.flocke.andotp",
        )
    }
}
