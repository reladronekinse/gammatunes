package com.gammatunes.app.lyrics

import android.util.Log
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private val cache = LinkedHashMap<String, LyricsResult>(48, 0.75f, true)

    private val noiseTitle = Regex(
        """\s*[\(\[\{][^\)\]\}]*?(official|video|audio|lyric|visuali[sz]er|remaster|live|hd|4k|explicit|mv)[^\)\]\}]*[\)\]\}]\s*""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun getLyrics(track: Track): LyricsResult? = withContext(Dispatchers.IO) {
        val key = track.videoId.ifBlank { "${track.artist}|${track.title}" }
        synchronized(cache) {
            cache[key]?.let { return@withContext it }
        }

        val title = cleanTitle(track.title)
        val artist = track.artist.trim()
        val album = track.album.orEmpty()

        val duration = track.durationSeconds ?: 0

        try {
            val resp = ApiClient.api.lyrics(
                title = title.ifBlank { track.title },
                artist = artist,
                album = album,
                duration = duration,
            )
            if (!resp.ok) {
                Log.w(TAG, "lyrics not found: ${resp.error}")
                return@withContext null
            }
            val result = when {
                !resp.lrc.isNullOrBlank() -> {
                    val lines = LrcParser.parse(resp.lrc)
                    if (lines.isEmpty()) null
                    else LyricsResult(lines = lines, synced = true, source = resp.source ?: "lrclib")
                }
                !resp.plain.isNullOrBlank() -> {

                    val plainLines = resp.plain.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { LyricLine(timeMs = 0L, text = it) }
                    if (plainLines.isEmpty()) null
                    else LyricsResult(lines = plainLines, synced = false, source = resp.source ?: "lrclib", plainText = resp.plain)
                }
                else -> null
            }
            if (result != null) {
                synchronized(cache) { cache[key] = result }
            }
            result
        } catch (t: Throwable) {
            Log.e(TAG, "lyrics request failed", t)
            null
        }
    }

    private fun cleanTitle(title: String): String =
        noiseTitle.replace(title, " ").replace(Regex("""\s+"""), " ").trim()
}
