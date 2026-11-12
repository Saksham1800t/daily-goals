package com.example.dailygoals.engine

/** The app under test and how long the test lasts; a null end means until it is switched off. */
data class BlockSession(val targetPackage: String, val untilMillis: Long?)

enum class ForegroundAction { SHOW_BLOCK, HIDE_BLOCK, NONE }

/**
 * Decides what to do when a different app comes to the front. Only package names are ever looked at,
 * never what is on screen.
 */
object ForegroundRules {
    /**
     * Opening an app from the recents screen makes the launcher report itself a moment after the app.
     * Within this time of a block appearing, a launcher event is not taken as leaving the app.
     */
    const val LAUNCHER_GRACE_MILLIS = 1000L

    /**
     * After the user is sent home, an app such as YouTube shrinks to a floating window and reports itself
     * again. For this long that report is not taken as the app being opened.
     */
    const val HOME_QUIET_MILLIS = 3000L

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
        launcherPackages: Set<String> = emptySet(),
        millisSinceShown: Long = Long.MAX_VALUE,
        millisSinceHome: Long = Long.MAX_VALUE,
    ): ForegroundAction {
        if (!isActive(session, nowMillis)) return if (blockShowing) ForegroundAction.HIDE_BLOCK else ForegroundAction.NONE
        if (foregroundPackage == ownPackage || foregroundPackage in passThrough) return ForegroundAction.NONE
        if (foregroundPackage == session?.targetPackage) {
            return if (blockShowing || millisSinceHome < HOME_QUIET_MILLIS) ForegroundAction.NONE else ForegroundAction.SHOW_BLOCK
        }
        if (!blockShowing) return ForegroundAction.NONE
        val launcherTransition = foregroundPackage in launcherPackages && millisSinceShown < LAUNCHER_GRACE_MILLIS
        return if (launcherTransition) ForegroundAction.NONE else ForegroundAction.HIDE_BLOCK
    }
}
