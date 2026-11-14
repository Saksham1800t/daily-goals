package com.example.dailygoals.engine

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Plain-text storage for the lists the blocker needs after a reboot. Fields are percent-encoded so a goal
 * name or a message can hold any character, and anything unreadable is skipped rather than crashing the
 * service.
 */
object BlockCodec {
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun dec(value: String) = URLDecoder.decode(value, "UTF-8")

    fun encodeBlocks(blocks: List<ActiveBlock>): String = blocks.joinToString("\n") {
        listOf(it.goalId.toString(), enc(it.goalName), enc(it.packageName), it.kind.name, it.endsAtMillis.toString(), enc(it.message.orEmpty()), enc(it.sourceDate.orEmpty()), if (it.allowTempAccess) "1" else "0")
            .joinToString("\t")
    }

    fun decodeBlocks(text: String): List<ActiveBlock> = text.lines().mapNotNull { line ->
        runCatching {
            val f = line.split('\t')
            ActiveBlock(f[0].toLong(), dec(f[1]), dec(f[2]), BlockKind.valueOf(f[3]), f[4].toLong(), dec(f[5]).ifEmpty { null }, f.getOrNull(6)?.let(::dec)?.ifEmpty { null }, f.getOrNull(7) != "0")
        }.getOrNull()
    }

    fun encodeUses(uses: List<PauseUse>): String = uses.joinToString("\n") { "${it.startedAtMillis}\t${it.endsAtMillis}" }

    fun decodeUses(text: String): List<PauseUse> = text.lines().mapNotNull { line ->
        runCatching { line.split('\t').let { PauseUse(it[0].toLong(), it[1].toLong()) } }.getOrNull()
    }

    fun encodeTempUses(uses: List<TempAccessUse>): String = uses.joinToString("\n") { "${enc(it.packageName)}\t${it.startedAtMillis}\t${it.endsAtMillis}" }

    fun decodeTempUses(text: String): List<TempAccessUse> = text.lines().mapNotNull { line ->
        runCatching { line.split('\t').let { TempAccessUse(dec(it[0]), it[1].toLong(), it[2].toLong()) } }.getOrNull()
    }

    fun encodeTempSettings(s: TempAccessSettings): String = listOf(if (s.enabled) 1 else 0, s.maxUsesPerDay, s.maxMinutes, s.waitSeconds).joinToString(",")

    fun decodeTempSettings(text: String): TempAccessSettings? = runCatching {
        text.split(',').map { it.trim().toInt() }.let { TempAccessSettings(it[0] == 1, it[1], it[2], it[3]).clamped() }
    }.getOrNull()

    fun encodePackages(packages: Set<String>): String = packages.sorted().joinToString("\n")

    fun decodePackages(text: String): Set<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
