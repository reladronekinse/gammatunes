package com.gammatunes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.gammatunes.app.ui.theme.LocalGlassTokens

/**
 * Имитация "Liquid Glass" — полупрозрачная, слегка размытая поверхность
 * со светящейся окантовкой сверху, как в новом дизайн-языке Apple/Android 16.
 *
 * Технически это не настоящее размытие фона под карточкой (для этого нужен
 * RenderEffect.createBlurEffect и захват содержимого позади, что сложнее),
 * а комбинация: полупрозрачная заливка + blur самого контента + градиентная
 * рамка-блик. Для большинства UI-кейсов (плеер, чипы, миниплеер) этого
 * достаточно, чтобы получить нужное ощущение "стекла".
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tokens.tint.copy(alpha = tokens.tint.alpha * 1.2f),
                        tokens.tint,
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tokens.border,
                        tokens.border.copy(alpha = 0.05f),
                        tokens.border.copy(alpha = 0.25f),
                    ),
                ),
                shape = shape,
            ),
    ) {
        content()
    }
}

/** Тонкая версия для чипов/бейджей поверх обложек. */
@Composable
fun LiquidGlassChip(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        content = content,
    )
}

internal val GlassHighlight = Color(0x66FFFFFF)
