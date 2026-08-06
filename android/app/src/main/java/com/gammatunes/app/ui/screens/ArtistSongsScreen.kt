@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gammatunes.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    artistId: String,
    onTrackClick: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var tracks by remember(artistId) { mutableStateOf<List<Track>>(emptyList()) }
    var title by remember(artistId) { mutableStateOf(strings.popularTracks) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        loading = true
        error = null
        try {
            val artist = ApiClient.api.artistDetail(artistId)
            title = artist.name
            tracks = artist.songs
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(strings.popularTracks, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (title.isNotBlank() && title != strings.popularTracks) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                }
            },
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("${strings.errorPrefix}$error", color = MaterialTheme.colorScheme.error)
            }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(strings.nothingFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
            ) {
                itemsIndexed(tracks, key = { i, t -> "$i:${t.videoId}" }) { index, track ->
                    ArtistSongRow(
                        number = index + 1,
                        track = track,
                        onClick = { onTrackClick(track, tracks) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSongRow(number: Int, track: Track, onClick: () -> Unit) {
    var showDownload by remember { mutableStateOf(false) }
    val indexMap by OfflineRepository.index.collectAsState()
    val downloadingIds by OfflineRepository.downloadingIds.collectAsState()
    val isDownloaded = indexMap.containsKey(track.videoId)
    val isDownloading = downloadingIds.contains(track.videoId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDownload = true },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        AsyncImage(
            model = track.thumbnail,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!track.album.isNullOrBlank()) {
                Text(
                    track.album!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDownload || isDownloading || isDownloaded) {
            DownloadButton(track = track)
        }
    }
    if (showDownload && !isDownloaded && !isDownloading) {
        LaunchedEffect(track.videoId) {
            OfflineRepository.download(track)
            kotlinx.coroutines.delay(2500)
            showDownload = false
        }
    }
}
