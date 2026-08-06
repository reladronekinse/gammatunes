package com.gammatunes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gammatunes.app.model.Track
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.DownloadButton
import com.gammatunes.app.ui.components.LiquidGlassSurface

@Composable
fun OfflineScreen(onTrackClick: (Track, List<Track>) -> Unit) {
    val index by OfflineRepository.index.collectAsState()
    val tracks = index.values.map { it.track }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Оффлайн",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Нет скачанных треков",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Нажмите на значок скачивания рядом с треком в поиске или в плеере",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(tracks, key = { it.videoId }) { track ->
                    OfflineTrackRow(track = track, onClick = { onTrackClick(track, tracks) })
                }
            }
        }
    }
}

@Composable
private fun OfflineTrackRow(track: Track, onClick: () -> Unit) {
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
