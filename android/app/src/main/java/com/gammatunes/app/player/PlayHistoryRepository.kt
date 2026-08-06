package com.gammatunes.app.player

import android.content.Context
import com.gammatunes.app.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayHistoryRepository {
    private const val PREFS = "ytm_play_history"
    private const val KEY = "recent"
    private const val MAX_SIZE = 40

    private val gson = Gson()
    private lateinit var appContext: Context

    private val _recent = MutableStateFlow<List<Track>>(emptyList())
    val recent: StateFlow<List<Track>> = _recent.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    fun record(track: Track) {
        if (!::appContext.isInitialized) return
        if (track.videoId.isBlank()) return
        val current = _recent.value.toMutableList()
        current.removeAll { it.videoId == track.videoId }
        current.add(0, track)
        while (current.size > MAX_SIZE) current.removeAt(current.lastIndex)
        _recent.value = current
        persist(current)
    }

    fun clear() {
        _recent.value = emptyList()
        if (::appContext.isInitialized) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        }
    }

    private fun load() {
        val json = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        try {
            val type = object : TypeToken<List<Track>>() {}.type
            val list: List<Track> = gson.fromJson(json, type) ?: emptyList()
            _recent.value = list
        } catch (_: Exception) {
            _recent.value = emptyList()
        }
    }

    private fun persist(list: List<Track>) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(list))
            .apply()
    }
}
