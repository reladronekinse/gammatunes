@file:OptIn(ExperimentalFoundationApi::class)

package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.gammatunes.app.ui.components.ZoomableImage
import com.gammatunes.app.model.AlbumTracksResponse
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.DownloadButton
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import com.gammatunes.app.ui.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onTrackClick: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var album by remember(albumId) { mutableStateOf<AlbumTracksResponse?>(null) }
    var isLoading by remember(albumId) { mutableStateOf(true) }
    var error by remember(albumId) { mutableStateOf<String?>(null) }

    LaunchedEffect(albumId) {
        isLoading = true
        error = null
        try {
            album = ApiClient.api.albumTracks(albumId)
        } catch (e: Exception) {
            error = e.message ?: "Failed to load album"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.title ?: strings.album, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    val loaded = album
                    if (loaded != null) {
                        AlbumDownloadIconButton(
                            albumId = loaded.albumId,
                            title = loaded.title,
                            thumbnail = loaded.thumbnail,
                            tracks = loaded.tracks,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${strings.errorPrefix}$error",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                album != null -> {
                    val loadedAlbum = album!!
                    Spacer(Modifier.height(8.dp))
                    AlbumHeader(title = loadedAlbum.title, thumbnail = loadedAlbum.thumbnail)
                    Spacer(Modifier.height(16.dp))
                    if (loadedAlbum.tracks.isEmpty()) {
                        Text(
                            text = strings.noTracksInAlbum,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            itemsIndexed(loadedAlbum.tracks, key = { i, t -> "${i}:${t.videoId}" }) { index, track ->
                                AlbumTrackRow(
                                    number = index + 1,
                                    track = track,
                                    onClick = { onTrackClick(track, loadedAlbum.tracks) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(number: Int, track: Track, onClick: () -> Unit) {
    var showDownload by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val index by OfflineRepository.index.collectAsState()
    val downloadingIds by OfflineRepository.downloadingIds.collectAsState()
    val isDownloaded = index.containsKey(track.videoId)
    val isDownloading = downloadingIds.contains(track.videoId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDownload = true },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun AlbumHeader(title: String, thumbnail: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ZoomableImage(
            model = thumbnail,
            contentDescription = title,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumDownloadIconButton(
    albumId: String,
    title: String,
    thumbnail: String?,
    tracks: List<Track>,
) {
    val strings = LocalStrings.current
    val albums by OfflineRepository.albums.collectAsState()
    val downloadingAlbums by OfflineRepository.downloadingAlbumIds.collectAsState()
    val isDownloaded = albums.containsKey(albumId)
    val isDownloading = downloadingAlbums.contains(albumId)
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                if (isDownloaded) {
                    OfflineRepository.deleteAlbum(albumId, deleteTrackFiles = true)
                } else if (!isDownloading && tracks.isNotEmpty()) {
                    OfflineRepository.downloadAlbum(
                        albumId = albumId,
                        title = title,
                        thumbnail = thumbnail,
                        tracks = tracks,
                    )
                }
            }
        },
        enabled = tracks.isNotEmpty() || isDownloaded,
    ) {
        when {
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            isDownloaded -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = strings.albumDownloaded,
                modifier = Modifier.size(22.dp),
            )
            else -> Icon(
                Icons.Default.Download,
                contentDescription = strings.downloadAlbum,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
