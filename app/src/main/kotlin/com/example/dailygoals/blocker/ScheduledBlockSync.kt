package com.example.dailygoals.blocker

import android.content.Context
import com.example.dailygoals.engine.ActiveBlock

/** Puts the scheduled blocks worked out from the goals into force, and removes the ones that ended. */
class ScheduledBlockSync(private val context: Context, private val registry: BlockRegistry) {
    fun apply(blocks: List<ActiveBlock>) {
        registry.dropExpired()
        registry.replaceScheduled(blocks)
        if (registry.hasActiveBlocks()) BlockerWatchdog.schedule(context)
        BlockerService.instance?.recheckFront()
    }
}
