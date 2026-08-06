package com.gammatunes.app.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class LyricsResult(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
    val plainText: String? = null,
)
