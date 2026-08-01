package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.model.Track
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.components.LiquidGlassSurface
import kotlinx.coroutines.launch
import com.gammatunes.app.ui.i18n.LocalStrings
import com.gammatunes.app.ui.i18n.LocaleRepository
import com.gammatunes.app.ui.i18n.AppLanguage
import androidx.compose.ui.platform.LocalContext


@Composable
fun MoreScreen(onTrackClick: (Track, List<Track>) -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val language by LocaleRepository.language.collectAsState()
    val isLoggedIn by AuthRepository.isLoggedIn.collectAsState()
    val accountHint by AuthRepository.accountHint.collectAsState()
    val likedTracks by AuthRepository.likedTracks.collectAsState()
    val statusMessage by AuthRepository.statusMessage.collectAsState()
    val isBusy by AuthRepository.isBusy.collectAsState()
    val offlineIndex by OfflineRepository.index.collectAsState()
    val offlineTracks = offlineIndex.values.map { it.track }
    val offlineAlbumsMap by OfflineRepository.albums.collectAsState()
    val offlineAlbums = offlineAlbumsMap.values.toList()

    var headersText by remember { mutableStateOf("") }
    var showLoginField by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && likedTracks.isEmpty()) {
            AuthRepository.refreshLiked()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = strings.moreTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
        }


        item {
            Text(
                text = strings.languageSection,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = language == AppLanguage.ENGLISH,
                        onClick = { LocaleRepository.setLanguage(context, AppLanguage.ENGLISH) },
                        label = { Text(strings.languageEnglish) },
                    )
                    FilterChip(
                        selected = language == AppLanguage.RUSSIAN,
                        onClick = { LocaleRepository.setLanguage(context, AppLanguage.RUSSIAN) },
                        label = { Text(strings.languageRussian) },
                    )
                }
            }
        }


        item {
            Text(
                text = strings.accountSection,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLoggedIn) strings.loggedIn else strings.guest,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = accountHint
                                    ?: if (isLoggedIn) strings.sessionActive else strings.needHeaders,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isLoggedIn) {
                            IconButton(onClick = { scope.launch { AuthRepository.logout() } }) {
                                Icon(Icons.Default.Logout, contentDescription = strings.logout)
                            }
                        }
                    }

                    if (!isLoggedIn) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = strings.loginHelp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!showLoginField) {
                            Button(
                                onClick = { showLoginField = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(strings.loginWithHeaders)
                            }
                        } else {
                            OutlinedTextField(
                                value = headersText,
                                onValueChange = { headersText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                placeholder = { Text(strings.headersPlaceholder) },
                                maxLines = 12,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val ok = AuthRepository.loginWithHeaders(headersText)
                                            if (ok) {
                                                headersText = ""
                                                showLoginField = false
                                                AuthRepository.refreshLiked()
                                            }
                                        }
                                    },
                                    enabled = headersText.isNotBlank() && !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Text(strings.saveAndLogin)
                                }
                                TextButton(onClick = { showLoginField = false }) {
                                    Text(strings.cancel)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { scope.launch { AuthRepository.refreshLiked() } },
                            enabled = !isBusy,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.refreshLikes)
                        }
                    }

                    if (isBusy) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    statusMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }


        if (isLoggedIn) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.likedSection.format(likedTracks.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (likedTracks.isEmpty() && !isBusy) {
                item {
                    Text(
                        text = strings.likedEmpty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(likedTracks, key = { "liked:${it.videoId}" }) { track ->
                TrackListRow(
                    track = track,
                    onClick = { onTrackClick(track, likedTracks) },
                )
            }
        }


        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(
                    Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.offlineAlbumsSection.format(offlineAlbums.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (offlineAlbums.isEmpty()) {
            item {
                Text(
                    text = strings.offlineAlbumsEmpty,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(offlineAlbums, key = { "album:${it.albumId}" }) { album ->
                OfflineAlbumRow(
                    album = album,
                    onClick = {
                        val tracks = OfflineRepository.albumTracksOrdered(album.albumId)
                        if (tracks.isNotEmpty()) {
                            onTrackClick(tracks.first(), tracks)
                        }
                    },
                    onDelete = {
                        scope.launch {
                            OfflineRepository.deleteAlbum(album.albumId, deleteTrackFiles = true)
                        }
                    },
                )
            }
        }


        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(
                    Icons.Default.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.offlineTracksSection.format(offlineTracks.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (offlineTracks.isEmpty()) {
            item {
                Text(
                    text = strings.offlineTracksEmpty,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(offlineTracks, key = { "off:${it.videoId}" }) { track ->
                TrackListRow(
                    track = track,
                    onClick = { onTrackClick(track, offlineTracks) },
                )
            }
        }


        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.aboutSection,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.versionLabel.format("0.1-unstable"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.licenseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackListRow(track: Track, onClick: () -> Unit) {
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
                model = track.thumbnail,
                contentDescription = track.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DownloadButton(track = track)
        }
    }
}


@Composable
private fun OfflineAlbumRow(
    album: com.gammatunes.app.offline.OfflineAlbum,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val tracks = OfflineRepository.albumTracksOrdered(album.albumId)
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
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.tracksCount.format(tracks.size) + (album.year?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = strings.deleteAlbum,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

