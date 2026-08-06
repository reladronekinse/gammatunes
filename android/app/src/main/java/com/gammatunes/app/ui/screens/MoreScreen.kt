package com.gammatunes.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings
import com.gammatunes.app.ui.i18n.LocaleRepository
import com.gammatunes.app.ui.i18n.AppLanguage
import androidx.compose.ui.platform.LocalContext

@Composable
fun MoreScreen(
    onOpenAccount: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenOfflineTracks: () -> Unit,
    onOpenOfflineAlbums: () -> Unit,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val language by LocaleRepository.language.collectAsState()
    val isLoggedIn by AuthRepository.isLoggedIn.collectAsState()
    val offlineIndex by OfflineRepository.index.collectAsState()
    val offlineAlbums by OfflineRepository.albums.collectAsState()

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
            SectionTitle(strings.accountSection)
        }
        item {
            MoreNavRow(
                icon = Icons.Default.AccountCircle,
                title = strings.accountSection,
                subtitle = if (isLoggedIn) strings.loggedIn else strings.guest,
                onClick = onOpenAccount,
            )
        }


        item {
            SectionTitle(strings.offlineSection)
        }
        item {
            MoreNavRow(
                icon = Icons.Default.OfflinePin,
                title = strings.offlineTracksTitle,
                subtitle = strings.offlineTracksSection.format(offlineIndex.size),
                onClick = onOpenOfflineTracks,
            )
        }
        item {
            MoreNavRow(
                icon = Icons.Default.Album,
                title = strings.offlineAlbumsTitle,
                subtitle = strings.offlineAlbumsSection.format(offlineAlbums.size),
                onClick = onOpenOfflineAlbums,
            )
        }


        item {
            SectionTitle(strings.uiSettingsSection)
        }
        item {
            MoreNavRow(
                icon = Icons.Default.Palette,
                title = strings.uiSettingsSection,
                subtitle = strings.appearanceSubtitle,
                onClick = onOpenAppearance,
            )
        }


        item {
            SectionTitle(strings.languageSection)
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
                        onClick = {
                            LocaleRepository.setLanguage(context, AppLanguage.ENGLISH)
                        },
                        label = { Text(strings.languageEnglish) },
                    )
                    FilterChip(
                        selected = language == AppLanguage.RUSSIAN,
                        onClick = {
                            LocaleRepository.setLanguage(context, AppLanguage.RUSSIAN)
                        },
                        label = { Text(strings.languageRussian) },
                    )
                }
            }
        }


        item {
            SectionTitle(strings.aboutSection)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.versionLabel.format("0.3-stable"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.licenseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.aboutBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MoreNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
