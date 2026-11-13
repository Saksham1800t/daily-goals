package com.example.dailygoals.engine

/** How firmly a goal answers a failure. Each level includes the one before it. */
enum class Intensity { NONE, GENTLE, MODERATE, STRONG }

/** What to do about one failure, decided from the goal's intensity and whether the emergency pause runs. */
data class PunishmentPlan(
    val notify: Boolean,
    val fullScreenAlert: Boolean,
    val blockPackages: Set<String>,
    val blockUntilMillis: Long?,
    val skippedByPause: Boolean,
) {
    val blocks: Boolean get() = blockUntilMillis != null && blockPackages.isNotEmpty()

    companion object {
        val NOTHING = PunishmentPlan(notify = false, fullScreenAlert = false, blockPackages = emptySet(), blockUntilMillis = null, skippedByPause = false)
    }
}

object PunishmentRules {
    val BLOCK_MINUTE_CHOICES = listOf(15, 30, 60, 120, 240, 480)
    const val DEFAULT_BLOCK_MINUTES = 60

    fun blocksApps(intensity: Intensity): Boolean = intensity >= Intensity.MODERATE

    /**
     * A failure is always recorded. During the emergency pause no punishment runs, and the failure is
     * marked so the review can tell. Safe apps are removed here as well as when the block is enforced, so
     * a package saved before it became safe never makes it into a block.
     */
    fun onFailure(
        intensity: Intensity,
        packages: Set<String>,
        blockMinutes: Int,
        failedAtMillis: Long,
        pauseActive: Boolean,
        safeApps: Set<String>,
        ownPackage: String,
    ): PunishmentPlan {
        if (intensity == Intensity.NONE) return PunishmentPlan.NOTHING
        if (pauseActive) return PunishmentPlan.NOTHING.copy(skippedByPause = true)
        val blockable = packages.filterNot { BlockSet.isSafe(it, safeApps, ownPackage) }.toSet()
        val blocks = blocksApps(intensity) && blockable.isNotEmpty() && blockMinutes > 0
        return PunishmentPlan(
            notify = true,
            fullScreenAlert = intensity == Intensity.STRONG,
            blockPackages = if (blocks) blockable else emptySet(),
            blockUntilMillis = if (blocks) failedAtMillis + blockMinutes * 60_000L else null,
            skippedByPause = false,
        )
    }
}
