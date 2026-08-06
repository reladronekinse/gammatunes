package com.gammatunes.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gammatunes.app.ui.settings.UiSettingsRepository

data class GlassTokens(
    val tint: Color,
    val border: Color,
)

val LocalGlassTokens = staticCompositionLocalOf {
    GlassTokens(tint = GlassTintDark, border = GlassBorderDark)
}

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,

    primaryContainer = lerp(accent, Color.Black, 0.45f),
    onPrimaryContainer = lerp(accent, Color.White, 0.90f),
    secondary = SeedSecondary,
    onSecondary = SeedOnSecondary,
    secondaryContainer = SeedSecondaryContainer,
    onSecondaryContainer = SeedOnSecondaryContainer,
    tertiary = SeedTertiary,
    onTertiary = SeedOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun GammaTunesTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val ui by UiSettingsRepository.settings.collectAsState()
    val coverAccent by DynamicAccent.coverAccent.collectAsState()

    val accentColor = if (ui.accentFromCover) {
        coverAccent ?: ui.accent.composeColor
    } else {
        ui.accent.composeColor
    }

    val colorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        buildDarkScheme(accentColor)
    }

    val glassTokens = GlassTokens(tint = GlassTintDark, border = GlassBorderDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(
        LocalGlassTokens provides glassTokens,
        LocalContentColor provides colorScheme.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
                ) {
                    content()
                }
            },
        )
    }
}
