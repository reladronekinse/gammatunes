package com.gammatunes.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gammatunes.app.model.Track
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun DownloadButton(
    track: Track,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 18.dp,
) {
    val strings = LocalStrings.current
    val index by OfflineRepository.index.collectAsState()
    val downloadingIds by OfflineRepository.downloadingIds.collectAsState()
    val errors by OfflineRepository.errors.collectAsState()
    val scope = rememberCoroutineScope()

    val isDownloaded = index.containsKey(track.videoId)
    val isDownloading = downloadingIds.contains(track.videoId)
    val hasError = errors.containsKey(track.videoId)

    Box(
        modifier = modifier
            .size(buttonSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = buttonSize / 2),
                onClick = {
                    scope.launch {
                        when {
                            isDownloading -> Unit
                            isDownloaded -> OfflineRepository.delete(track.videoId)
                            else -> OfflineRepository.download(track)
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
            )
            isDownloaded -> Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = strings.downloaded,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hasError -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = errors[track.videoId] ?: strings.download,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.error,
            )
            else -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = strings.download,
                modifier = Modifier.size(iconSize),
                tint = LocalContentColor.current,
            )
        }
    }
}
