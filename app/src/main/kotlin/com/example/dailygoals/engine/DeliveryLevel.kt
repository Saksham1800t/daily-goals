package com.example.dailygoals.engine

/** How a warning can reach the user, best first. */
enum class DeliveryLevel { FULL_SCREEN, HEADS_UP, NORMAL, IN_APP }

data class DeliveryState(
    val notificationsEnabled: Boolean,
    val channelAllowsHeadsUp: Boolean,
    val fullScreenAllowed: Boolean,
)

object DeliveryLevels {
    /**
     * The highest level that is currently allowed. Android may refuse full-screen intents or the user
     * may turn the sound or pop-up off, so nothing is assumed: with notifications off the warning is
     * only shown inside the app.
     */
    fun choose(state: DeliveryState, strongAlertWanted: Boolean): DeliveryLevel = when {
        !state.notificationsEnabled -> DeliveryLevel.IN_APP
        strongAlertWanted && state.fullScreenAllowed -> DeliveryLevel.FULL_SCREEN
        state.channelAllowsHeadsUp -> DeliveryLevel.HEADS_UP
        else -> DeliveryLevel.NORMAL
    }
}
