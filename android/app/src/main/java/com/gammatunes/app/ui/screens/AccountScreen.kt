package com.gammatunes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val isLoggedIn by AuthRepository.isLoggedIn.collectAsState()
    val accountHint by AuthRepository.accountHint.collectAsState()
    val likedTracks by AuthRepository.likedTracks.collectAsState()
    val playlists by AuthRepository.playlists.collectAsState()
    val statusMessage by AuthRepository.statusMessage.collectAsState()
    val isBusy by AuthRepository.isBusy.collectAsState()
    val scope = rememberCoroutineScope()

    var headersText by remember { mutableStateOf("") }
    var showLoginField by remember { mutableStateOf(false) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var expandedPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            if (likedTracks.isEmpty()) AuthRepository.refreshLiked()
            if (playlists.isEmpty()) AuthRepository.refreshPlaylists()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    strings.accountSection,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (isLoggedIn) {
                                accountHint?.takeIf { it.isNotBlank() } ?: strings.loggedIn
                            } else {
                                strings.guest
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        if (!statusMessage.isNullOrBlank()) {
                            Text(
                                text = statusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = strings.loginHelp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (!isLoggedIn) {
                            if (!showLoginField) {
                                Button(
                                    onClick = { showLoginField = true },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth(),
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
                                    placeholder = { Text(strings.needHeaders) },
                                    maxLines = 8,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ok = AuthRepository.loginWithHeaders(headersText)
                                                if (ok) {
                                                    showLoginField = false
                                                    headersText = ""
                                                }
                                            }
                                        },
                                        enabled = !isBusy && headersText.isNotBlank(),
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text(strings.saveAndLogin)
                                        }
                                    }
                                    TextButton(onClick = { showLoginField = false }) {
                                        Text(strings.cancel)
                                    }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { scope.launch { AuthRepository.logout() } },
                                    enabled = !isBusy,
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.logout)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            AuthRepository.refreshLiked()
                                            AuthRepository.refreshPlaylists()
                                        }
                                    },
                                    enabled = !isBusy,
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.refreshLikes)
                                }
                            }
                        }
                    }
                }
            }

            if (isLoggedIn) {
                item {
                    Text(
                        text = strings.likedSection.format(likedTracks.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (likedTracks.isEmpty()) {
                    item {
                        Text(
                            strings.likedEmpty,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(likedTracks, key = { "like:${it.videoId}" }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, likedTracks) },
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.playlistsSection,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { scope.launch { AuthRepository.refreshPlaylists() } },
                            enabled = !isBusy,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = strings.refreshPlaylists)
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            strings.playlistsEmpty,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(playlists, key = { "pl:${it.playlistId}" }) { pl ->
                        val expanded = expandedPlaylistId == pl.playlistId
                        LiquidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (expanded) {
                                                expandedPlaylistId = null
                                                expandedPlaylistTracks = emptyList()
                                            } else {
                                                expandedPlaylistId = pl.playlistId
                                                playlistLoading = true
                                                scope.launch {
                                                    try {
                                                        val detail =
                                                            ApiClient.api.playlistTracks(pl.playlistId)
                                                        expandedPlaylistTracks = detail.tracks
                                                    } catch (_: Throwable) {
                                                        expandedPlaylistTracks = emptyList()
                                                    } finally {
                                                        playlistLoading = false
                                                    }
                                                }
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pl.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                        )
                                        pl.count?.let {
                                            Text(
                                                strings.tracksCount.format(it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Icon(
                                        if (expanded) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                    )
                                }
                                if (expanded) {
                                    if (playlistLoading) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    } else if (expandedPlaylistTracks.isEmpty()) {
                                        Text(
                                            strings.playlistTracksEmpty,
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        expandedPlaylistTracks.forEach { track ->
                                            TrackRow(
                                                track = track,
                                                onClick = {
                                                    onTrackClick(track, expandedPlaylistTracks)
                                                },
                                            )
                                            Spacer(Modifier.height(6.dp))
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
}
