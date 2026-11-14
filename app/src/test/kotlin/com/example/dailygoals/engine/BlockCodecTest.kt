package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockCodecTest {
    @Test
    fun blocksSurviveAnyCharacterInTheirText() {
        val blocks = listOf(
            ActiveBlock(1, "Wake up\tearly\nplease", "com.instagram.android", BlockKind.PUNISHMENT, 9_000, "No {app}!\nBack to 100%"),
            ActiveBlock(2, "Study", "com.youtube", BlockKind.SCHEDULED, 12_000, null),
            ActiveBlock(3, "Gym", "com.youtube", BlockKind.PUNISHMENT, 15_000, null, sourceDate = "2026-11-13", allowTempAccess = false),
        )
        assertEquals(blocks, BlockCodec.decodeBlocks(BlockCodec.encodeBlocks(blocks)))
    }

    @Test
    fun aBlockStoredBeforeTheNewerFieldsExistedStillLoads() {
        val old = "1\tWake\tcom.a\tPUNISHMENT\t9000\t"
        assertEquals(listOf(ActiveBlock(1, "Wake", "com.a", BlockKind.PUNISHMENT, 9_000)), BlockCodec.decodeBlocks(old))
    }

    @Test
    fun temporaryAccessUsesRoundTrip() {
        val uses = listOf(TempAccessUse("com.instagram.android", 1_000, 2_000), TempAccessUse("com.youtube", 5_000, 9_000))
        assertEquals(uses, BlockCodec.decodeTempUses(BlockCodec.encodeTempUses(uses)))
        assertEquals(emptyList<TempAccessUse>(), BlockCodec.decodeTempUses("x"))
        val settings = TempAccessSettings(enabled = false, maxUsesPerDay = 4, maxMinutes = 25, waitSeconds = 90)
        assertEquals(settings, BlockCodec.decodeTempSettings(BlockCodec.encodeTempSettings(settings)))
        assertEquals(null, BlockCodec.decodeTempSettings("1,2"))
    }

    @Test
    fun anEmptyOrDamagedStoreGivesAnEmptyList() {
        assertEquals(emptyList<ActiveBlock>(), BlockCodec.decodeBlocks(""))
        assertEquals(emptyList<ActiveBlock>(), BlockCodec.decodeBlocks("not a block\n1\t2"))
        assertEquals(emptyList<PauseUse>(), BlockCodec.decodeUses("x\ty"))
    }

    @Test
    fun pauseUsesAndPackageListsRoundTrip() {
        val uses = listOf(PauseUse(1_000, 2_000), PauseUse(5_000, 9_000))
        assertEquals(uses, BlockCodec.decodeUses(BlockCodec.encodeUses(uses)))
        val packages = setOf("com.android.dialer", "com.google.android.deskclock")
        assertEquals(packages, BlockCodec.decodePackages(BlockCodec.encodePackages(packages)))
        assertEquals(emptySet<String>(), BlockCodec.decodePackages(""))
    }
}
