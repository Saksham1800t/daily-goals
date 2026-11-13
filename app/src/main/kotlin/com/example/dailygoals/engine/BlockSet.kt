package com.example.dailygoals.engine

enum class BlockKind { SCHEDULED, PUNISHMENT }

/** One goal's block of one app. Each goal's block is kept on its own so correcting one removes only it. */
data class ActiveBlock(
    val goalId: Long,
    val goalName: String,
    val packageName: String,
    val kind: BlockKind,
    val endsAtMillis: Long,
    val message: String? = null,
)

/** Everything that blocks one app right now, ordered by when each block ends; [longest] ends last. */
data class AppBlocks(val packageName: String, val blocks: List<ActiveBlock>) {
    val longest: ActiveBlock get() = blocks.last()
    val others: List<ActiveBlock> get() = blocks.dropLast(1)
}

object BlockSet {
    /** Safe apps are never blocked, even when a package was saved on a goal before it became safe. */
    fun isSafe(packageName: String, safeApps: Set<String>, ownPackage: String): Boolean =
        packageName == ownPackage || packageName in safeApps

    fun activeBlocks(blocks: List<ActiveBlock>, safeApps: Set<String>, ownPackage: String, nowMillis: Long): List<ActiveBlock> =
        blocks.filter { nowMillis < it.endsAtMillis && !isSafe(it.packageName, safeApps, ownPackage) }

    /** The conflict display: for each blocked app, every goal blocking it, with the longest block last. */
    fun conflicts(blocks: List<ActiveBlock>, safeApps: Set<String>, ownPackage: String, nowMillis: Long): List<AppBlocks> =
        activeBlocks(blocks, safeApps, ownPackage, nowMillis)
            .groupBy { it.packageName }
            .map { (pkg, list) -> AppBlocks(pkg, list.sortedWith(compareBy({ it.endsAtMillis }, { it.goalId }))) }
            .sortedBy { it.packageName }

    /** The block the service should enforce for [packageName], or null when it may open. */
    fun blockFor(
        packageName: String,
        blocks: List<ActiveBlock>,
        safeApps: Set<String>,
        ownPackage: String,
        pauseActive: Boolean,
        nowMillis: Long,
    ): AppBlocks? {
        if (pauseActive) return null
        return conflicts(blocks, safeApps, ownPackage, nowMillis).firstOrNull { it.packageName == packageName }
    }

    /** Correcting a failure removes only that goal's blocks; the app stays blocked while any other lists it. */
    fun withoutGoal(blocks: List<ActiveBlock>, goalId: Long): List<ActiveBlock> = blocks.filterNot { it.goalId == goalId }

    fun withoutExpired(blocks: List<ActiveBlock>, nowMillis: Long): List<ActiveBlock> = blocks.filter { nowMillis < it.endsAtMillis }
}
