package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.player.PlayerState
import com.gammatunes.app.player.RepeatMode
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.components.LiquidGlassSurface
import kotlinx.coroutines.launch
import com.gammatunes.app.ui.i18n.LocalStrings


@Composable
fun PlayerScreen(
    player: PlayerState,
    onArtistClick: (artistId: String) -> Unit = {},
) {
    val strings = LocalStrings.current
    val track = player.currentTrack
    val scope = rememberCoroutineScope()

    if (track == null) {
        EmptyPlayerPlaceholder()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = track.title,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
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

            Spacer(Modifier.height(20.dp))

            run {
                var dragPositionMs by remember { mutableStateOf<Float?>(null) }
                val durationMs = player.durationMs
                val sliderMax = if (durationMs > 0) durationMs.toFloat() else 1f
                val displayedPositionMs = (dragPositionMs ?: player.currentPositionMs.toFloat())
                    .coerceIn(0f, sliderMax)

                Column(modifier = Modifier.fillMaxWidth(0.85f)) {
                    Slider(
                        value = displayedPositionMs,
                        onValueChange = {
                            player.isSeeking = true
                            dragPositionMs = it
                        },
                        onValueChangeFinished = {
                            dragPositionMs?.let { player.seekTo(it.toLong()) }
                            dragPositionMs = null
                            player.isSeeking = false
                        },
                        valueRange = 0f..sliderMax,
                        enabled = durationMs > 0,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(displayedPositionMs.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.75f),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                    val scope = rememberCoroutineScope()
                    IconButton(
                        onClick = {
                            if (!isLoggedIn) return@IconButton
                            scope.launch {
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
                    DownloadButton(track = track)
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
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

