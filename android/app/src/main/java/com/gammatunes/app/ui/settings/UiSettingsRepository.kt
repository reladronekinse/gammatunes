package com.gammatunes.app.ui.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CoverStyle(val id: String) {
    SQUARE("square"),
    ROUNDED("rounded"),
    CIRCLE("circle"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: ROUNDED
    }
}

enum class SeekBarStyle(val id: String) {
    DEFAULT("default"),
    THIN("thin"),
    WAVE("wave"),
    SQUIGGLE("squiggle"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: DEFAULT
    }
}

enum class AccentOption(val id: String, val color: Long) {
    RED("red", 0xFFE53935),
    ORANGE("orange", 0xFFFF8A00),
    AMBER("amber", 0xFFFFC107),
    GREEN("green", 0xFF43A047),
    TEAL("teal", 0xFF00897B),
    BLUE("blue", 0xFF1E88E5),
    PURPLE("purple", 0xFF8E24AA),
    PINK("pink", 0xFFD81B60),
    ;

    val composeColor: Color get() = Color(color)

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: RED
    }
}

enum class BackgroundStyle(val id: String) {
    BLUR_ART("blur_art"),
    FULL_COVER("full_cover"),
    SOLID_DARK("solid_dark"),
    GRADIENT("gradient"),
    ;

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id } ?: BLUR_ART
    }
}

data class UiSettings(
    val coverStyle: CoverStyle = CoverStyle.ROUNDED,
    val seekBarStyle: SeekBarStyle = SeekBarStyle.DEFAULT,
    val accent: AccentOption = AccentOption.RED,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.BLUR_ART,

    val accentFromCover: Boolean = false,
)

object UiSettingsRepository {
    private const val PREFS = "ytm_ui_settings"
    private const val KEY_COVER = "cover"
    private const val KEY_SEEK = "seek"
    private const val KEY_ACCENT = "accent"
    private const val KEY_BG = "background"
    private const val KEY_ACCENT_FROM_COVER = "accent_from_cover"

    private val _settings = MutableStateFlow(UiSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = UiSettings(
            coverStyle = CoverStyle.fromId(prefs.getString(KEY_COVER, null)),
            seekBarStyle = SeekBarStyle.fromId(prefs.getString(KEY_SEEK, null)),
            accent = AccentOption.fromId(prefs.getString(KEY_ACCENT, null)),
            backgroundStyle = BackgroundStyle.fromId(prefs.getString(KEY_BG, null)),
            accentFromCover = prefs.getBoolean(KEY_ACCENT_FROM_COVER, false),
        )
    }

    private fun persist(context: Context, s: UiSettings) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COVER, s.coverStyle.id)
            .putString(KEY_SEEK, s.seekBarStyle.id)
            .putString(KEY_ACCENT, s.accent.id)
            .putString(KEY_BG, s.backgroundStyle.id)
            .putBoolean(KEY_ACCENT_FROM_COVER, s.accentFromCover)
            .apply()
        _settings.value = s
    }

    fun setCoverStyle(context: Context, style: CoverStyle) {
        persist(context, _settings.value.copy(coverStyle = style))
    }

    fun setSeekBarStyle(context: Context, style: SeekBarStyle) {
        persist(context, _settings.value.copy(seekBarStyle = style))
    }

    fun setAccent(context: Context, accent: AccentOption) {
        persist(context, _settings.value.copy(accent = accent))
    }

    fun setBackgroundStyle(context: Context, style: BackgroundStyle) {
        persist(context, _settings.value.copy(backgroundStyle = style))
    }

    fun setAccentFromCover(context: Context, enabled: Boolean) {
        persist(context, _settings.value.copy(accentFromCover = enabled))
    }
}
