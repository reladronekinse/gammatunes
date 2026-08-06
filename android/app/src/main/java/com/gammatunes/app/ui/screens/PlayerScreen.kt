package com.gammatunes.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.lyrics.LyricsRepository
import com.gammatunes.app.lyrics.LyricsResult
import com.gammatunes.app.lyrics.LyricLine
import com.gammatunes.app.model.PlaylistSummary
import com.gammatunes.app.player.PlayerState
import com.gammatunes.app.player.RepeatMode
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings
import com.gammatunes.app.ui.settings.BackgroundStyle
import com.gammatunes.app.ui.settings.CoverStyle
import com.gammatunes.app.ui.settings.SeekBarStyle
import com.gammatunes.app.ui.settings.UiSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    player: PlayerState,
    onArtistClick: (artistId: String) -> Unit = {},
    onAlbumClick: (albumId: String) -> Unit = {},
) {
    val strings = LocalStrings.current
    val track = player.currentTrack
    val ui by UiSettingsRepository.settings.collectAsState()
    val scope = rememberCoroutineScope()

    if (track == null) {
        EmptyPlayerPlaceholder()
        return
    }


    var positionMs by remember(track.videoId) { mutableLongStateOf(0L) }
    var durationMs by remember(track.videoId) { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember(track.videoId) { mutableStateOf(false) }
    var lyrics by remember(track.videoId) { mutableStateOf<LyricsResult?>(null) }
    var lyricsLoading by remember(track.videoId) { mutableStateOf(false) }
    var lyricsError by remember(track.videoId) { mutableStateOf<String?>(null) }
    val lyricsListState = rememberLazyListState()

    LaunchedEffect(track.videoId, showLyrics) {
        if (!showLyrics) return@LaunchedEffect
        if (lyrics != null) return@LaunchedEffect
        lyricsLoading = true
        lyricsError = null

        var durSec = track.durationSeconds ?: 0
        if (durSec <= 0) {
            delay(400)
            val d = player.durationMs
            if (d > 0) durSec = (d / 1000L).toInt()
        }
        val trackWithDur = if (durSec > 0 && track.durationSeconds == null) {
            track.copy(durationSeconds = durSec)
        } else track
        val result = LyricsRepository.getLyrics(trackWithDur)
        lyrics = result
        if (result == null) lyricsError = strings.lyricsNotFound
        lyricsLoading = false
    }

    LaunchedEffect(track.videoId, player.isPlaying) {
        while (true) {
            if (!isSeeking) {
                positionMs = player.positionMs
                durationMs = player.durationMs
            }
            delay(250)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (ui.backgroundStyle) {
            BackgroundStyle.BLUR_ART -> {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)),
                )
            }
            BackgroundStyle.FULL_COVER -> {

                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (showLyrics) Modifier.blur(32.dp) else Modifier),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                if (showLyrics) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                } else {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.25f),
                                        0.45f to Color.Black.copy(alpha = 0.15f),
                                        0.75f to Color.Black.copy(alpha = 0.55f),
                                        1.0f to Color.Black.copy(alpha = 0.82f),
                                    ),
                                ),
                            ),
                    )
                }
            }
            BackgroundStyle.SOLID_DARK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            BackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val coverShape = when (ui.coverStyle) {
                CoverStyle.SQUARE -> RoundedCornerShape(4.dp)
                CoverStyle.ROUNDED -> RoundedCornerShape(28.dp)
                CoverStyle.CIRCLE -> CircleShape
            }


            val showCoverInSlot = ui.backgroundStyle != BackgroundStyle.FULL_COVER
            val artSpacer by animateDpAsState(
                targetValue = if (showLyrics) 12.dp else 24.dp,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "artSpacer",
            )
            val coverAlpha by animateFloatAsState(
                targetValue = if (showLyrics || !showCoverInSlot) 0f else 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "coverAlpha",
            )
            val lyricsAlpha by animateFloatAsState(
                targetValue = if (showLyrics) 1f else 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "lyricsAlpha",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .then(
                        if (showLyrics || !showCoverInSlot) {
                            Modifier.weight(1f, fill = true)
                        } else {
                            Modifier.aspectRatio(1f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showCoverInSlot) {
                    AsyncImage(
                        model = track.thumbnail,
                        contentDescription = track.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = coverAlpha }
                            .clip(coverShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                if (showLyrics || lyricsAlpha > 0.01f) {
                    SyncedLyricsView(
                        lines = lyrics?.lines.orEmpty(),
                        synced = lyrics?.synced == true,
                        positionMs = positionMs,
                        loading = lyricsLoading,
                        error = lyricsError,
                        listState = lyricsListState,
                        onSeekToLine = { line ->
                            if (lyrics?.synced == true) player.seekTo(line.timeMs)
                        },
                        backdropBlur = ui.backgroundStyle == BackgroundStyle.FULL_COVER,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lyricsAlpha },
                    )
                }
            }
            Spacer(modifier = Modifier.height(artSpacer))

            val canOpenAlbum = !track.albumId.isNullOrBlank()
            AutoSizeSingleLineText(
                text = track.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(0f, 1f),
                        blurRadius = 5f,
                    ),
                ),
                color = Color.White,
                maxFontSize = 22.sp,
                minFontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .then(
                        if (canOpenAlbum) {
                            Modifier.clickable { onAlbumClick(track.albumId!!) }
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge.copy(

                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f,
                    ),
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {

                    val id = track.artistId
                    if (!id.isNullOrBlank()) {
                        onArtistClick(id)
                    } else {
                        scope.launch {
                            try {
                                val found = com.gammatunes.app.network.ApiClient.api
                                    .searchArtists(track.artist)
                                    .artists
                                    .firstOrNull()
                                if (found != null) {
                                    onArtistClick(found.artistId)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))


            val safeDuration = durationMs.coerceAtLeast(1L)
            val sliderValue = if (isSeeking) {
                seekFraction
            } else {
                (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
            }
            Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                val displayPos = if (isSeeking) {
                    (seekFraction * safeDuration).toLong()
                } else {
                    positionMs
                }
                val enabled = durationMs > 0 && !player.isLoadingStream
                when (ui.seekBarStyle) {
                    SeekBarStyle.DEFAULT -> {
                        Slider(
                            value = sliderValue,
                            onValueChange = { v ->
                                isSeeking = true
                                seekFraction = v
                            },
                            onValueChangeFinished = {
                                val target = (seekFraction * safeDuration).toLong()
                                player.seekTo(target)
                                positionMs = target
                                isSeeking = false
                            },
                            enabled = enabled,
                        )
                    }
                    SeekBarStyle.THIN -> {
                        Slider(
                            value = sliderValue,
                            onValueChange = { v ->
                                isSeeking = true
                                seekFraction = v
                            },
                            onValueChangeFinished = {
                                val target = (seekFraction * safeDuration).toLong()
                                player.seekTo(target)
                                positionMs = target
                                isSeeking = false
                            },
                            enabled = enabled,
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    SeekBarStyle.WAVE -> {
                        WaveSeekBar(
                            progress = sliderValue,
                            enabled = enabled,
                            onProgressChange = { v ->
                                isSeeking = true
                                seekFraction = v
                            },
                            onProgressChangeFinished = {
                                val target = (seekFraction * safeDuration).toLong()
                                player.seekTo(target)
                                positionMs = target
                                isSeeking = false
                            },
                        )
                    }
                    SeekBarStyle.SQUIGGLE -> {
                        SquiggleSeekBar(
                            progress = sliderValue,
                            enabled = enabled,
                            onProgressChange = { v ->
                                isSeeking = true
                                seekFraction = v
                            },
                            onProgressChangeFinished = {
                                val target = (seekFraction * safeDuration).toLong()
                                player.seekTo(target)
                                positionMs = target
                                isSeeking = false
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(displayPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                    IconButton(onClick = { player.cycleRepeatMode() }) {
                        val isActive = player.repeatMode != RepeatMode.OFF
                        Icon(
                            imageVector = if (player.repeatMode == RepeatMode.ONE) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            contentDescription = when (player.repeatMode) {
                                RepeatMode.OFF -> strings.repeatOff
                                RepeatMode.ALL -> strings.repeatAll
                                RepeatMode.ONE -> strings.repeatOne
                            },
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
                LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                    val isLoggedIn by AuthRepository.isLoggedIn.collectAsState()
                    val liked by AuthRepository.likedTracks.collectAsState()
                    val isLiked = liked.any { it.videoId == track.videoId }
                    val likeScope = rememberCoroutineScope()
                    IconButton(
                        onClick = {
                            if (!isLoggedIn) return@IconButton
                            likeScope.launch {
                                if (isLiked) {
                                    AuthRepository.unlikeTrack(track.videoId)
                                    AuthRepository.refreshLiked()
                                } else {
                                    AuthRepository.likeTrack(track.videoId)
                                    AuthRepository.refreshLiked()
                                }
                            }
                        },
                        enabled = isLoggedIn,
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) strings.unlike else strings.like,
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                    DownloadButton(track = track, buttonSize = 48.dp, iconSize = 24.dp)
                }
                LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = strings.lyrics,
                            tint = if (showLyrics) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
                LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                    var showPlaylistPicker by remember { mutableStateOf(false) }
                    val isLoggedIn by AuthRepository.isLoggedIn.collectAsState()
                    IconButton(
                        onClick = {
                            if (isLoggedIn) showPlaylistPicker = true
                        },
                        enabled = isLoggedIn,
                    ) {
                        Icon(
                            Icons.Default.PlaylistAdd,
                            contentDescription = strings.addToPlaylist,
                        )
                    }
                    if (showPlaylistPicker) {
                        AddToPlaylistDialog(
                            videoId = track.videoId,
                            onDismiss = { showPlaylistPicker = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LiquidGlassSurface(shape = RoundedCornerShape(50)) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IconButton(
                        onClick = { player.playPrevious() },
                        enabled = player.hasPrevious,
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = strings.previous)
                    }
                    IconButton(
                        onClick = { player.togglePlayPause() },
                        enabled = !player.isLoadingStream,
                    ) {
                        Icon(
                            imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (player.isPlaying) strings.pause else strings.play,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(
                        onClick = { player.playNext() },
                        enabled = player.hasNext,
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = strings.next)
                    }
                }
            }

            if (player.isLoadingStream) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            player.streamError?.let {
                Spacer(Modifier.height(16.dp))
                Text("${strings.errorPrefix}$it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SquiggleSeekBar(
    progress: Float,
    enabled: Boolean,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.28f)
    var widthPx by remember { mutableStateOf(1f) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onProgressChange((offset.x / widthPx).coerceIn(0f, 1f))
                    onProgressChangeFinished()
                }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onProgressChangeFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onProgressChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val amp = h * 0.32f
        val wavelength = w / 5.5f
        val stroke = 4.5.dp.toPx()
        val progressX = (progress.coerceIn(0f, 1f) * w)

        fun yAt(x: Float): Float {
            val t = x / wavelength

            return midY + amp * (
                0.72f * kotlin.math.sin(t * 2f * Math.PI.toFloat()) +
                    0.28f * kotlin.math.sin(t * 4f * Math.PI.toFloat() + 0.6f)
                )
        }

        fun buildPath(fromX: Float, toX: Float): androidx.compose.ui.graphics.Path {
            val path = androidx.compose.ui.graphics.Path()
            val steps = ((toX - fromX) / 3f).toInt().coerceAtLeast(2)
            path.moveTo(fromX, yAt(fromX))
            for (i in 1..steps) {
                val x = fromX + (toX - fromX) * (i / steps.toFloat())
                path.lineTo(x, yAt(x))
            }
            return path
        }


        drawPath(
            path = buildPath(0f, w),
            color = trackColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )

        if (progressX > 1f) {
            drawPath(
                path = buildPath(0f, progressX),
                color = primary,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }

        val ty = yAt(progressX)
        drawCircle(
            color = primary.copy(alpha = 0.25f),
            radius = 11.dp.toPx(),
            center = Offset(progressX, ty),
        )
        drawCircle(
            color = primary,
            radius = 6.dp.toPx(),
            center = Offset(progressX, ty),
        )
    }
}

@Composable
private fun WaveSeekBar(
    progress: Float,
    enabled: Boolean,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = Color.White.copy(alpha = 0.25f)
    var widthPx by remember { mutableStateOf(1f) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onProgressChange((offset.x / widthPx).coerceIn(0f, 1f))
                    onProgressChangeFinished()
                }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onProgressChangeFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onProgressChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    },
                )
            },
    ) {
        val bars = 32
        val barWidth = size.width / (bars * 1.8f)
        val gap = barWidth * 0.8f
        for (i in 0 until bars) {
            val x = i * (barWidth + gap) + gap
            val mid = bars / 2f
            val amp = 0.35f + 0.65f * (1f - kotlin.math.abs(i - mid) / mid)
            val h = size.height * amp * (0.55f + 0.45f * kotlin.math.sin(i * 0.9f).toFloat().let { (it + 1f) / 2f })
            val active = i / bars.toFloat() <= progress
            drawRoundRect(
                color = if (active) primary else track,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000L).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun AutoSizeSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }


    LaunchedEffect(text) {
        fontSize = maxFontSize
    }

    Text(
        text = text,
        color = color,
        style = style,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minFontSize.value + 0.4f) {
                val next = (fontSize.value - 1f).coerceAtLeast(minFontSize.value)
                fontSize = next.sp
            }
        },
    )
}

@Composable
private fun EmptyPlayerPlaceholder() {
    val strings = LocalStrings.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.playerEmptyTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.playerEmptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    videoId: String,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val playlists by AuthRepository.playlists.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (playlists.isEmpty()) {
            AuthRepository.refreshPlaylists()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addToPlaylist) },
        text = {
            Column {
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (playlists.isEmpty()) {
                    Text(
                        strings.playlistsEmpty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    playlists.forEach { pl: PlaylistSummary ->
                        Text(
                            text = pl.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loading) {
                                    scope.launch {
                                        loading = true
                                        message = null
                                        val ok = AuthRepository.addToPlaylist(pl.playlistId, videoId)
                                        loading = false
                                        message = if (ok) {
                                            strings.addedToPlaylist
                                        } else {
                                            strings.addToPlaylistFailed
                                        }
                                        if (ok) {
                                            kotlinx.coroutines.delay(600)
                                            onDismiss()
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    synced: Boolean,
    positionMs: Long,
    loading: Boolean,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeekToLine: (LyricLine) -> Unit,
    backdropBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val activeIndex = remember(positionMs, lines, synced) {
        when {
            lines.isEmpty() -> -1
            !synced -> -1
            else -> lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        }
    }


    LaunchedEffect(activeIndex, viewportHeightPx) {
        if (activeIndex < 0 || viewportHeightPx <= 0) return@LaunchedEffect
        try {
            listState.animateScrollToItem(activeIndex, scrollOffset = 0)
        } catch (_: Exception) {
            try {
                listState.scrollToItem(activeIndex, scrollOffset = 0)
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportHeightPx = it.height }
            .then(
                if (backdropBlur) {
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(
                error,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 6f,
                    ),
                ),
                modifier = Modifier.padding(16.dp),
            )
            lines.isEmpty() -> Text(
                strings.lyricsNotFound,
                color = Color.White.copy(alpha = 0.85f),
            )
            else -> {
                val pad = with(density) {

                    (viewportHeightPx / 2).coerceAtLeast(0).toDp()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = pad),
                    userScrollEnabled = true,
                ) {
                    itemsIndexed(lines, key = { i, line -> "$i:${line.timeMs}" }) { index, line ->
                        val active = index == activeIndex || (!synced && index == 0)
                        Text(
                            text = line.text,
                            style = (if (active && synced) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            }).copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.9f),
                                    offset = Offset(0f, 1.5f),
                                    blurRadius = 8f,
                                ),
                            ),
                            color = when {
                                active && synced -> MaterialTheme.colorScheme.primary
                                !synced -> Color.White.copy(alpha = 0.9f)
                                else -> Color.White.copy(alpha = 0.55f)
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekToLine(line) }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
