package com.example.dailygoals.engine

data class MessageValues(
    val app: String? = null,
    val goal: String? = null,
    val until: String? = null,
    val timeLeft: String? = null,
    val why: String? = null,
    val attempts: Int? = null,
)

sealed interface MessageCheck {
    data class Ok(val text: String) : MessageCheck
    data object Empty : MessageCheck
    data class TooLong(val length: Int) : MessageCheck
}

/** The text on the block screen: defaults, the 160 character rule and the placeholders. */
object BlockMessages {
    const val MAX_LENGTH = 160
    const val DEFAULT_SCHEDULED = "{app} is blocked until {until} because of your goal: {goal}."
    const val DEFAULT_PUNISHMENT = "You missed {goal}, so {app} is blocked until {until}."

    val PLACEHOLDERS = listOf("{app}", "{goal}", "{until}", "{time_left}", "{why}", "{attempts}")

    val SAMPLE = MessageValues(app = "Instagram", goal = "Study", until = "18:00", timeLeft = "1 h 20 min", why = "I want to pass the exam", attempts = 3)

    fun default(kind: BlockKind): String = if (kind == BlockKind.SCHEDULED) DEFAULT_SCHEDULED else DEFAULT_PUNISHMENT

    fun check(text: String): MessageCheck {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> MessageCheck.Empty
            trimmed.length > MAX_LENGTH -> MessageCheck.TooLong(trimmed.length)
            else -> MessageCheck.Ok(trimmed)
        }
    }

    /** A goal's own message wins, then the default the user set, then the built-in one. */
    fun choose(goalMessage: String?, settingsDefault: String?, kind: BlockKind): String =
        listOf(goalMessage, settingsDefault).firstNotNullOfOrNull { (check(it.orEmpty()) as? MessageCheck.Ok)?.text } ?: default(kind)

    /** Unknown placeholders stay as typed; a known one with no value is dropped and the gap tidied. */
    fun render(template: String, values: MessageValues): String {
        val replacements = mapOf(
            "{app}" to values.app,
            "{goal}" to values.goal,
            "{until}" to values.until,
            "{time_left}" to values.timeLeft,
            "{why}" to values.why,
            "{attempts}" to values.attempts?.toString(),
        )
        var result = template
        var hadMissing = false
        replacements.forEach { (key, value) ->
            if (value == null) {
                if (key in result) hadMissing = true
                result = result.replace(key, "")
            } else {
                result = result.replace(key, value)
            }
        }
        return if (hadMissing) tidy(result) else result
    }

    private fun tidy(text: String): String =
        text.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([.,;:!?])"), "$1").trim()
}
