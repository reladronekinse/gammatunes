package com.gammatunes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.model.Album
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.ui.i18n.LocalStrings

enum class ArtistReleaseKind { ALBUMS, SINGLES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsGridScreen(
    artistId: String,
    kind: ArtistReleaseKind,
    onAlbumClick: (Album) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var albums by remember(artistId, kind) { mutableStateOf<List<Album>>(emptyList()) }
    var artistName by remember(artistId) { mutableStateOf("") }
    var loading by remember(artistId, kind) { mutableStateOf(true) }
    var error by remember(artistId, kind) { mutableStateOf<String?>(null) }

    val sectionTitle = when (kind) {
        ArtistReleaseKind.ALBUMS -> strings.albumsSection
        ArtistReleaseKind.SINGLES -> strings.singlesSection
    }

    LaunchedEffect(artistId, kind) {
        loading = true
        error = null
        try {
            val artist = ApiClient.api.artistDetail(artistId)
            artistName = artist.name
            albums = when (kind) {
                ArtistReleaseKind.ALBUMS -> artist.albums
                ArtistReleaseKind.SINGLES -> artist.singles
            }
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
                    Text(sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (artistName.isNotBlank()) {
                        Text(
                            artistName,
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
            albums.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(strings.noAlbums, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.albumId }) { album ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album) },
                    ) {
                        if (!album.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = album.thumbnail,
                                contentDescription = album.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Album, contentDescription = null)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            album.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        album.year?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
