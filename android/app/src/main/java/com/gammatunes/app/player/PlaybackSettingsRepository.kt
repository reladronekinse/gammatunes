package com.gammatunes.app.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Streaming audio quality, mirrored on the backend (backend_server.py / backend/main.py).
 * The [apiValue] is sent as the `quality` query param to the `/stream/{videoId}` endpoint.
 */
enum class AudioQuality(val apiValue: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.apiValue == id } ?: HIGH
    }
}

data class PlaybackSettings(
    val quality: AudioQuality = AudioQuality.HIGH,
)

object PlaybackSettingsRepository {
    private const val PREFS = "ytm_playback_settings"
    private const val KEY_QUALITY = "quality"

    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = PlaybackSettings(
            quality = AudioQuality.fromId(prefs.getString(KEY_QUALITY, null)),
        )
    }

    fun setQuality(context: Context, quality: AudioQuality) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY, quality.apiValue)
            .apply()
        _settings.value = _settings.value.copy(quality = quality)
    }
}
