package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gammatunes.app.model.Album
import com.gammatunes.app.model.Artist
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings

/**
 * Страница артиста. Альбомы и синглы/EP — две отдельные горизонтальные ленты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onAlbumClick: (Album) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var artist by remember(artistId) { mutableStateOf<Artist?>(null) }
    var isLoading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        isLoading = true
        error = null
        try {
            artist = ApiClient.api.artistDetail(artistId)
        } catch (e: Exception) {
            error = e.message ?: "Failed to load artist"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        artist?.name ?: strings.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                artist != null -> {
                    val loadedArtist = artist!!
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            ArtistHeader(artist = loadedArtist)
                            Spacer(Modifier.height(20.dp))
                        }

                        // ---- Альбомы ----
                        if (loadedArtist.albums.isEmpty() && loadedArtist.singles.isEmpty()) {
                            item {
                                Text(
                                    text = strings.noAlbums,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (loadedArtist.albums.isNotEmpty()) {
                            item {
                                Text(
                                    text = strings.albumsCount.format(loadedArtist.albums.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(end = 8.dp, bottom = 20.dp),
                                ) {
                                    items(loadedArtist.albums, key = { "album:${it.albumId}" }) { album ->
                                        AlbumCardHorizontal(
                                            album = album,
                                            onClick = { onAlbumClick(album) },
                                        )
                                    }
                                }
                            }
                        }

                        // ---- Синглы / EP ----
                        if (loadedArtist.singles.isNotEmpty()) {
                            item {
                                Text(
                                    text = strings.singlesCount.format(loadedArtist.singles.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(end = 8.dp, bottom = 8.dp),
                                ) {
                                    items(loadedArtist.singles, key = { "single:${it.albumId}" }) { album ->
                                        AlbumCardHorizontal(
                                            album = album,
                                            onClick = { onAlbumClick(album) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(artist: Artist) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = artist.thumbnail,
            contentDescription = artist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Горизонтальная карточка альбома/сингла в ленте артиста. */
@Composable
private fun AlbumCardHorizontal(album: Album, onClick: () -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = album.thumbnail,
                contentDescription = album.title,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
            // Название по центру, шрифт уменьшается — без «...».
            AutoSizeSingleLineText(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxFontSize = 14.sp,
                minFontSize = 9.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            album.year?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Строка альбома (используется и в других экранах при необходимости). */
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit) {
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
                model = album.thumbnail,
                contentDescription = album.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album.year?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
