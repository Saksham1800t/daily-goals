package com.example.dailygoals.engine

/** The app under test and how long the test lasts; a null end means until it is switched off. */
data class BlockSession(val targetPackage: String, val untilMillis: Long?)

enum class ForegroundAction { SHOW_BLOCK, HIDE_BLOCK, NONE }

/**
 * Decides what to do when a different app comes to the front. Only package names are ever looked at,
 * never what is on screen.
 */
object ForegroundRules {
    fun isActive(session: BlockSession?, nowMillis: Long): Boolean =
        session != null && (session.untilMillis == null || nowMillis < session.untilMillis)

    /**
     * [passThrough] holds packages whose windows appear over any app without being a real switch, such as
     * the system bars and keyboards; they never show or remove the block. The block stays up while the
     * blocked app is in front and is removed as soon as another real app takes over.
     */
    fun decide(
        foregroundPackage: String,
        ownPackage: String,
        session: BlockSession?,
        blockShowing: Boolean,
        nowMillis: Long,
        passThrough: Set<String>,
    ): ForegroundAction {
        if (!isActive(session, nowMillis)) return if (blockShowing) ForegroundAction.HIDE_BLOCK else ForegroundAction.NONE
        if (foregroundPackage == ownPackage || foregroundPackage in passThrough) return ForegroundAction.NONE
        if (foregroundPackage == session?.targetPackage) return if (blockShowing) ForegroundAction.NONE else ForegroundAction.SHOW_BLOCK
        return if (blockShowing) ForegroundAction.HIDE_BLOCK else ForegroundAction.NONE
    }
}
