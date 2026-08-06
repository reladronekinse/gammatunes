package com.gammatunes.app.lyrics

object LrcParser {
    private val lineRegex = Regex("""^\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?](.*)$""")

    fun parse(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:")
            ) {
                continue
            }

            val tags = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""").findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in tags) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val frac = m.groupValues[3]
                val ms = when {
                    frac.isEmpty() -> 0L
                    frac.length == 1 -> frac.toLong() * 100L
                    frac.length == 2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                out.add(LyricLine(timeMs = min * 60_000L + sec * 1_000L + ms, text = text))
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
