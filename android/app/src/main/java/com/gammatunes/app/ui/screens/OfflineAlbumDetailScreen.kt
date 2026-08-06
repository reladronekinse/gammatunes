package com.gammatunes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.model.Track
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAlbumDetailScreen(
    albumId: String,
    onTrackClick: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val albums by OfflineRepository.albums.collectAsState()
    val album = albums[albumId]
    val tracks = OfflineRepository.albumTracksOrdered(albumId)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    album?.title ?: strings.album,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                }
            },
            actions = {
                if (album != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                OfflineRepository.deleteAlbum(albumId, deleteTrackFiles = true)
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = strings.deleteAlbum,
                        )
                    }
                }
            },
        )

        if (album == null || tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.noTracksInAlbum,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (!album.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = album.thumbnail,
                                contentDescription = album.title,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(160.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Album, contentDescription = null)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            album.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            strings.tracksCount.format(tracks.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, tracks) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString(),
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
                    }
                }
            }
        }
    }
}
