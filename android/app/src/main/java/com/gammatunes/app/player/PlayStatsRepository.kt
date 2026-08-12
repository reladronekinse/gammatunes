package com.gammatunes.app.player

import android.content.Context
import com.gammatunes.app.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArtistStat(
    val artistName: String,
    val artistId: String?,
    val playCount: Int,
    val thumbnail: String?,
)

/**
 * Tracks how many times each track has been played so the app can surface
 * "Top 15" most-listened tracks and "Top 3" most-listened artists.
 */
object PlayStatsRepository {
    private const val PREFS = "ytm_play_stats"
    private const val KEY = "stats_v1"
    private const val MAX_TOP_TRACKS = 15
    private const val MAX_TOP_ARTISTS = 3

    data class TrackStatEntry(val track: Track, val count: Int)

    private val gson = Gson()
    private lateinit var appContext: Context

    private val _trackStats = MutableStateFlow<Map<String, TrackStatEntry>>(emptyMap())

    private val _topTracks = MutableStateFlow<List<Track>>(emptyList())
    val topTracks: StateFlow<List<Track>> = _topTracks.asStateFlow()

    private val _topArtists = MutableStateFlow<List<ArtistStat>>(emptyList())
    val topArtists: StateFlow<List<ArtistStat>> = _topArtists.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    fun record(track: Track) {
        if (!::appContext.isInitialized) return
        if (track.videoId.isBlank()) return
        val current = _trackStats.value.toMutableMap()
        val existingCount = current[track.videoId]?.count ?: 0
        current[track.videoId] = TrackStatEntry(track = track, count = existingCount + 1)
        _trackStats.value = current
        recompute(current)
        persist(current)
    }

    fun playCountFor(videoId: String): Int = _trackStats.value[videoId]?.count ?: 0

    fun clear() {
        _trackStats.value = emptyMap()
        _topTracks.value = emptyList()
        _topArtists.value = emptyList()
        if (::appContext.isInitialized) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        }
    }

    private fun recompute(map: Map<String, TrackStatEntry>) {
        _topTracks.value = map.values
            .sortedByDescending { it.count }
            .take(MAX_TOP_TRACKS)
            .map { it.track }

        val byArtist = LinkedHashMap<String, MutableList<TrackStatEntry>>()
        for (entry in map.values) {
            val key = entry.track.artistId?.takeIf { it.isNotBlank() } ?: entry.track.artist
            byArtist.getOrPut(key) { mutableListOf() }.add(entry)
        }
        _topArtists.value = byArtist.values
            .mapNotNull { entries ->
                val best = entries.maxByOrNull { it.count } ?: return@mapNotNull null
                ArtistStat(
                    artistName = best.track.artist,
                    artistId = best.track.artistId,
                    playCount = entries.sumOf { it.count },
                    thumbnail = entries.firstOrNull { !it.track.thumbnail.isNullOrBlank() }?.track?.thumbnail
                        ?: best.track.thumbnail,
                )
            }
            .sortedByDescending { it.playCount }
            .take(MAX_TOP_ARTISTS)
    }

    private fun load() {
        val json = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        try {
            val type = object : TypeToken<Map<String, TrackStatEntry>>() {}.type
            val map: Map<String, TrackStatEntry> = gson.fromJson(json, type) ?: emptyMap()
            _trackStats.value = map
            recompute(map)
        } catch (_: Exception) {
            _trackStats.value = emptyMap()
        }
    }

    private fun persist(map: Map<String, TrackStatEntry>) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(map))
            .apply()
    }
}
