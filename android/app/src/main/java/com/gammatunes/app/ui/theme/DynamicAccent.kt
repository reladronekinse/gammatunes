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
import kotlin.math.max

object DynamicAccent {
    private val _coverAccent = MutableStateFlow<Color?>(null)
    val coverAccent: StateFlow<Color?> = _coverAccent.asStateFlow()

    private var lastUrl: String? = null


    private const val MIN_SATURATION = 0.72f

    private const val MIN_LIGHTNESS = 0.52f
    private const val MAX_LIGHTNESS = 0.68f

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
            val palette = Palette.from(scaled).maximumColorCount(16).generate()

            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            swatch?.rgb?.let { boostForDarkTheme(Color(it)) }
        } catch (_: Exception) {
            null
        }
    }


    private fun boostForDarkTheme(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)

        hsl[1] = max(hsl[1] * 1.35f, MIN_SATURATION).coerceIn(0f, 1f)

        hsl[2] = when {
            hsl[2] < MIN_LIGHTNESS -> MIN_LIGHTNESS
            hsl[2] > MAX_LIGHTNESS -> MAX_LIGHTNESS
            else -> hsl[2]
        }
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
