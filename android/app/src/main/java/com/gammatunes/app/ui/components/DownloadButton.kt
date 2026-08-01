package com.gammatunes.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gammatunes.app.model.Track
import com.gammatunes.app.offline.OfflineRepository
import kotlinx.coroutines.launch
import com.gammatunes.app.ui.i18n.LocalStrings


@Composable
fun DownloadButton(track: Track, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val index by OfflineRepository.index.collectAsState()
    val downloadingIds by OfflineRepository.downloadingIds.collectAsState()
    val errors by OfflineRepository.errors.collectAsState()
    val scope = rememberCoroutineScope()

    val isDownloaded = index.containsKey(track.videoId)
    val isDownloading = downloadingIds.contains(track.videoId)
    val hasError = errors.containsKey(track.videoId)

    IconButton(
        modifier = modifier,
        onClick = {
            scope.launch {
                if (isDownloaded) {
                    OfflineRepository.delete(track.videoId)
                } else if (!isDownloading) {
                    OfflineRepository.download(track)
                }
            }
        },
    ) {
        when {
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            isDownloaded -> Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = strings.downloaded,
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = if (hasError) strings.downloadError else strings.download,
                tint = if (hasError) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }
    }
}

