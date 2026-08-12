@file:OptIn(ExperimentalFoundationApi::class)

package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.player.PlayHistoryRepository
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.backend.LocalBackend
import com.gammatunes.app.model.Artist
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Composable
fun SearchScreen(onArtistClick: (Artist) -> Unit, onTrackClick: (Track, List<Track>) -> Unit) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var artistResults by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var trackResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank()) return
        isLoading = true
        error = null
        hasSearched = true
        scope.launch {
            try {


                if (LocalBackend.lastError != null) {
                    error = LocalBackend.lastError
                    artistResults = emptyList()
                    trackResults = emptyList()
                    return@launch
                }
                val ready = LocalBackend.awaitReady(timeoutMs = 8_000)
                if (!ready) {
                    error = strings.offlineOrBackendError
                    artistResults = emptyList()
                    trackResults = emptyList()
                    return@launch
                }
                coroutineScope {
                    val artistsDeferred = async {
                        runCatching { ApiClient.api.searchArtists(query).artists }
                            .getOrElse { emptyList() }
                    }
                    val tracksDeferred = async {
                        runCatching { ApiClient.api.searchTracks(query).results }
                            .getOrElse { emptyList() }
                    }
                    val artists = artistsDeferred.await()
                    val tracks = tracksDeferred.await()
                    artistResults = artists
                    trackResults = tracks
                    if (artists.isEmpty() && tracks.isEmpty()) {


                        try {
                            ApiClient.api.searchTracks(query)
                        } catch (t: Throwable) {
                            error = friendlyNetworkError(t, strings)
                        }
                    }
                }
            } catch (t: Throwable) {
                artistResults = emptyList()
                trackResults = emptyList()
                error = friendlyNetworkError(t, strings)
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(strings.searchPlaceholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { runSearch() },
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
        )

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        error?.let {
            Text(
                text = "${strings.errorPrefix}$it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (!isLoading && error == null && hasSearched && artistResults.isEmpty() && trackResults.isEmpty()) {
            Text(
                text = strings.nothingFound,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        val recent by PlayHistoryRepository.recent.collectAsState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {

            if (!hasSearched && !isLoading && recent.isNotEmpty()) {
                item {
                    Text(
                        text = strings.recentlyPlayed,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                items(recent, key = { "recent:${it.videoId}" }) { track ->
                    TrackRow(track = track, onClick = { onTrackClick(track, recent) })
                }
            }
            if (artistResults.isNotEmpty()) {
                item {
                    Text(
                        text = strings.artists,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                items(artistResults, key = { "artist:${it.artistId}" }) { artist ->
                    ArtistResultRow(artist = artist, onClick = { onArtistClick(artist) })
                }
            }
            if (trackResults.isNotEmpty()) {
                item {
                    Text(
                        text = strings.tracks,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(trackResults, key = { "track:${it.videoId}" }) { track ->
                    TrackRow(track = track, onClick = { onTrackClick(track, trackResults) })
                }
            }
        }
    }
}

private fun friendlyNetworkError(t: Throwable, strings: com.gammatunes.app.ui.i18n.AppStrings): String {
    val cause = generateSequence(t) { it.cause }.firstOrNull {
        it is UnknownHostException || it is SocketTimeoutException || it is IOException
    }
    return when {
        cause is UnknownHostException -> strings.offlineOrBackendError
        cause is SocketTimeoutException -> strings.offlineOrBackendError
        t.message?.contains("Failed to connect", ignoreCase = true) == true -> strings.offlineOrBackendError
        t.message?.contains("Unable to resolve host", ignoreCase = true) == true -> strings.offlineOrBackendError
        t.message?.contains("Connection refused", ignoreCase = true) == true -> strings.offlineOrBackendError
        else -> t.message ?: strings.offlineOrBackendError
    }
}

@Composable
private fun ArtistResultRow(artist: Artist, onClick: () -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    var showDownload by remember { mutableStateOf(false) }
    val index by OfflineRepository.index.collectAsState()
    val downloadingIds by OfflineRepository.downloadingIds.collectAsState()
    val isDownloaded = index.containsKey(track.videoId)
    val isDownloading = downloadingIds.contains(track.videoId)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDownload = true },
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                if (track.isVideo) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .padding(1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (track.isVideo) {
                        listOfNotNull(track.artist, "Video").joinToString(" · ")
                    } else {
                        track.artist
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showDownload || isDownloading || isDownloaded) {
                DownloadButton(track = track)
            }
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
