package com.gammatunes.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gammatunes.app.player.PlayStatsRepository
import com.gammatunes.app.ui.components.ArtistGridCell
import com.gammatunes.app.ui.i18n.LocalStrings

/**
 * "Top 3 artists" / "Топ-3 артиста" — the user's 3 most-listened artists, shown as a grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopArtistsScreen(
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val topArtists by PlayStatsRepository.topArtists.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(strings.topArtistsTitle, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                    Text(
                        strings.topArtistsSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        if (topArtists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.topArtistsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(topArtists, key = { _, stat -> stat.artistId ?: stat.artistName }) { index, stat ->
                    ArtistGridCell(
                        stat = stat,
                        onClick = {
                            val id = stat.artistId
                            if (!id.isNullOrBlank()) onArtistClick(id)
                        },
                        subtitle = strings.playsCountLabel.format(stat.playCount),
                        rank = index + 1,
                    )
                }
            }
        }
    }
}
