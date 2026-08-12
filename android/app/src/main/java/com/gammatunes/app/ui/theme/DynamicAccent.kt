package com.gammatunes.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

object DynamicAccent {
    private val _coverAccent = MutableStateFlow<Color?>(null)
    val coverAccent: StateFlow<Color?> = _coverAccent.asStateFlow()

    private var lastUrl: String? = null

    fun clear() {
        lastUrl = null
        _coverAccent.value = null
    }

    suspend fun updateFromThumbnail(context: Context, thumbnailUrl: String?) {
        if (thumbnailUrl.isNullOrBlank()) {
            clear()
            return
        }
        if (thumbnailUrl == lastUrl) return
        lastUrl = thumbnailUrl

        val color = withContext(Dispatchers.IO) {
            extractDominant(context, thumbnailUrl)
        }

        if (lastUrl == thumbnailUrl) {
            _coverAccent.value = color
        }
    }

    private suspend fun extractDominant(context: Context, url: String): Color? {
        return try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result !is SuccessResult) return null
            val drawable = result.drawable
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> return null
            }
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            val scaled = if (bitmap.width > 200 || bitmap.height > 200) {
                Bitmap.createScaledBitmap(bitmap, 200, 200, true)
            } else {
                bitmap
            }
            val palette = Palette.from(scaled)
                .maximumColorCount(24)
                .clearFilters()
                .generate()

            val candidate = pickBestSwatch(palette) ?: return null
            boostForDarkTheme(Color(candidate.rgb))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Prefer saturated, mid-lightness swatches that actually represent the artwork.
     * Avoid near-black / near-white and very desaturated greys.
     */
    private fun pickBestSwatch(palette: Palette): Palette.Swatch? {
        val all = buildList {
            palette.vibrantSwatch?.let { add(it) }
            palette.lightVibrantSwatch?.let { add(it) }
            palette.darkVibrantSwatch?.let { add(it) }
            palette.mutedSwatch?.let { add(it) }
            palette.lightMutedSwatch?.let { add(it) }
            palette.darkMutedSwatch?.let { add(it) }
            palette.dominantSwatch?.let { add(it) }
            addAll(palette.swatches)
        }.distinctBy { it.rgb }

        if (all.isEmpty()) return null

        fun score(swatch: Palette.Swatch): Float {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(swatch.rgb, hsl)
            val sat = hsl[1]
            val light = hsl[2]
            // Reject near-black, near-white, and grey
            if (light < 0.08f || light > 0.92f) return -1f
            if (sat < 0.12f) return -1f
            // Favour higher saturation and mid lightness; weight by population a bit
            val lightScore = 1f - abs(light - 0.55f) * 1.6f
            val popScore = (swatch.population / 10_000f).coerceAtMost(1f)
            return sat * 2.2f + lightScore * 0.9f + popScore * 0.35f
        }

        return all
            .map { it to score(it) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }
            ?.first
            ?: palette.dominantSwatch
            ?: all.firstOrNull()
    }

    /**
     * Adjust colour so it stays readable as a primary accent on a dark theme
     * without washing out the original hue.
     */
    private fun boostForDarkTheme(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)

        // Keep saturation natural; only lift very dull colours a little
        hsl[1] = when {
            hsl[1] < 0.25f -> (hsl[1] * 1.8f).coerceAtMost(0.55f)
            hsl[1] < 0.45f -> (hsl[1] * 1.25f).coerceAtMost(0.75f)
            else -> hsl[1].coerceIn(0.35f, 0.92f)
        }

        // Ensure accent is visible on dark background without becoming pastel
        hsl[2] = when {
            hsl[2] < 0.35f -> 0.42f + (hsl[2] * 0.25f)
            hsl[2] > 0.75f -> 0.58f
            else -> hsl[2].coerceIn(0.40f, 0.70f)
        }

        return Color(ColorUtils.HSLToColor(hsl))
    }
}
