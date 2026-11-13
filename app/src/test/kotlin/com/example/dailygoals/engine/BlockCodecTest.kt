package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockCodecTest {
    @Test
    fun blocksSurviveAnyCharacterInTheirText() {
        val blocks = listOf(
            ActiveBlock(1, "Wake up\tearly\nplease", "com.instagram.android", BlockKind.PUNISHMENT, 9_000, "No {app}!\nBack to 100%"),
            ActiveBlock(2, "Study", "com.youtube", BlockKind.SCHEDULED, 12_000, null),
        )
        assertEquals(blocks, BlockCodec.decodeBlocks(BlockCodec.encodeBlocks(blocks)))
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
