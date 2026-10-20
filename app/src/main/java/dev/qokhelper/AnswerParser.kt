package dev.qokhelper

sealed interface Answer {
    /** [position] counts answer buttons from the top, 1-4. */
    data class Found(val position: Int, val text: String, val confidence: Int?) : Answer
    data object NoQuestion : Answer
    data class Unparsed(val raw: String) : Answer
    data class Failed(val message: String) : Answer
}

object AnswerParser {
    private val LINE = Regex("""^\s*([1-4])\s*\|\s*([^|]+?)\s*(?:\|\s*(\d{1,3})\s*%?)?\s*$""")

    fun parse(reply: String): Answer {
        val text = normalizeDigits(reply).trim()
        if (text.equals("NONE", ignoreCase = true)) return Answer.NoQuestion
        for (line in text.lines()) {
            val m = LINE.find(line) ?: continue
            return Answer.Found(
                position = m.groupValues[1].toInt(),
                text = m.groupValues[2],
                confidence = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt(),
            )
        }
        return Answer.Unparsed(text)
    }

    /**
     * Persian and Arabic-Indic digits -> ASCII in the position and confidence fields only;
     * the answer text may legitimately contain Persian digits (e.g. a year).
     */
    private fun normalizeDigits(s: String): String {
        val parts = s.split('|')
        if (parts.size < 2) return s
        val out = parts.toMutableList()
        out[0] = toAscii(out[0])
        if (out.size >= 3) out[out.lastIndex] = toAscii(out.last())
        return out.joinToString("|")
    }

    private fun toAscii(s: String) = buildString {
        for (c in s) append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }
}
