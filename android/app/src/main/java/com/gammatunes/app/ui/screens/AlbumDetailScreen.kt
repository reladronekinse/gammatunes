package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.gammatunes.app.model.AlbumTracksResponse
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.DownloadButton
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import com.gammatunes.app.ui.i18n.LocalStrings

/**
 * Полноценная страница альбома со списком треков — раньше это тоже был
 * всплывающий Dialog поверх страницы артиста, теперь обычный экран в
 * NavHost (та же причина, что и для страницы артиста: попап неудобно
 * скроллить и он не даёт нормально работать системной кнопке "назад").
 */
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
                    Spacer(Modifier.height(12.dp))
                    AlbumDownloadRow(
                        albumId = loadedAlbum.albumId,
                        title = loadedAlbum.title,
                        thumbnail = loadedAlbum.thumbnail,
                        tracks = loadedAlbum.tracks,
                    )
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
                            itemsIndexed(loadedAlbum.tracks, key = { _, t -> t.videoId }) { index, track ->
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

/**
 * Строка трека в треклисте альбома. В отличие от TrackRow (используется в
 * поиске) — без обложки: в контексте одного альбома у всех треков она и так
 * одна и та же (обложка альбома уже показана в шапке экрана), поэтому
 * повторять её у каждой строки избыточно — только занимает место. Вместо
 * обложки — порядковый номер трека в альбоме, как в самом YouTube Music.
 * Ряд компактнее, чем TrackRow (меньше отступов, без карточки-подложки).
 */
@Composable
private fun AlbumTrackRow(number: Int, track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        DownloadButton(track = track)
    }
}

@Composable
private fun AlbumHeader(title: String, thumbnail: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
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
private fun AlbumDownloadRow(
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    if (isDownloaded) {
                        OfflineRepository.deleteAlbum(albumId, deleteTrackFiles = true)
                    } else if (!isDownloading) {
                        OfflineRepository.downloadAlbum(
                            albumId = albumId,
                            title = title,
                            thumbnail = thumbnail,
                            tracks = tracks,
                        )
                    }
                }
            },
            enabled = tracks.isNotEmpty() && !isDownloading,
        ) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(strings.downloadingAlbum)
            } else if (isDownloaded) {
                Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.albumDownloaded)
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.downloadAlbum)
            }
        }
    }
}
