package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryLevelTest {
    private fun choose(enabled: Boolean, headsUp: Boolean, fullScreen: Boolean, strong: Boolean) =
        DeliveryLevels.choose(DeliveryState(enabled, headsUp, fullScreen), strong)

    @Test
    fun aStrongWarningOpensFullScreenOnlyWhenAllowed() {
        assertEquals(DeliveryLevel.FULL_SCREEN, choose(enabled = true, headsUp = true, fullScreen = true, strong = true))
        assertEquals(DeliveryLevel.HEADS_UP, choose(enabled = true, headsUp = true, fullScreen = false, strong = true))
    }

    @Test
    fun aNormalWarningIsAHeadsUpNotificationWhileTheChannelAllowsIt() {
        assertEquals(DeliveryLevel.HEADS_UP, choose(enabled = true, headsUp = true, fullScreen = true, strong = false))
        assertEquals(DeliveryLevel.NORMAL, choose(enabled = true, headsUp = false, fullScreen = true, strong = false))
        assertEquals(DeliveryLevel.NORMAL, choose(enabled = true, headsUp = false, fullScreen = false, strong = true))
    }

    @Test
    fun withNotificationsOffTheWarningIsOnlyShownInsideTheApp() {
        assertEquals(DeliveryLevel.IN_APP, choose(enabled = false, headsUp = true, fullScreen = true, strong = true))
        assertEquals(DeliveryLevel.IN_APP, choose(enabled = false, headsUp = false, fullScreen = false, strong = false))
    }
}
