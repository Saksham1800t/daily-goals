package com.example.dailygoals.blocker

import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.engine.BlockCodec
import com.example.dailygoals.engine.CoolingOff
import com.example.dailygoals.engine.TempAccessSettings

/** The setting keys used for held changes. */
object PendingKeys {
    const val TEMP_ACCESS = "temp_access"
    const val SAFE_APP_PREFIX = "safe_app:"
}

/**
 * The one door for settings that can weaken a running block. A weakening change is held until the block
 * ends and anything else is applied at once; the decision itself is made by the engine.
 */
class CoolingOffGate(private val registry: BlockRegistry, private val repository: GoalRepository) {
    /** Returns when the change will apply, or null when it applied now. */
    suspend fun setSafeApp(packageName: String, safe: Boolean): Long? {
        val key = PendingKeys.SAFE_APP_PREFIX + packageName
        repository.cancelPendingSetting(key)
        val appliesAt = if (safe) CoolingOff.safeAppAppliesAt(packageName, registry.blocks, System.currentTimeMillis()) else null
        if (appliesAt != null) {
            repository.queueSetting(key, packageName, appliesAt)
            return appliesAt
        }
        registry.safeApps = if (safe) registry.safeApps + packageName else registry.safeApps - packageName
        BlockerService.instance?.recheckFront()
        return null
    }

    /** The stricter part of a change applies now; a looser part waits while any block runs. */
    suspend fun setTempSettings(wanted: TempAccessSettings): Long? {
        repository.cancelPendingSetting(PendingKeys.TEMP_ACCESS)
        val old = registry.tempSettings
        val new = wanted.clamped()
        val appliesAt = CoolingOff.tempSettingsApplyAt(old, new, registry.blocks, System.currentTimeMillis())
        if (appliesAt == null) {
            registry.tempSettings = new
            return null
        }
        registry.tempSettings = CoolingOff.stricterOf(old, new)
        repository.queueSetting(PendingKeys.TEMP_ACCESS, BlockCodec.encodeTempSettings(new), appliesAt)
        return appliesAt
    }

    companion object {
        /** Carries out a held settings change once its block has ended. */
        fun applySetting(registry: BlockRegistry, key: String, value: String) {
            when {
                key == PendingKeys.TEMP_ACCESS -> BlockCodec.decodeTempSettings(value)?.let { registry.tempSettings = it }
                key.startsWith(PendingKeys.SAFE_APP_PREFIX) -> registry.safeApps = registry.safeApps + value
            }
        }
    }
}
