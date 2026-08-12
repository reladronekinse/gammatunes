package com.gammatunes.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gammatunes.app.ui.components.LiquidGlassSurface
import com.gammatunes.app.ui.i18n.LocalStrings
import com.gammatunes.app.update.AppUpdateRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val state by AppUpdateRepository.state.collectAsState()
    val currentState = rememberUpdatedState(state)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                currentState.value is AppUpdateRepository.UpdateState.AwaitingInstallPermission
            ) {
                AppUpdateRepository.resumeInstallAfterPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    strings.updatesSection,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(strings.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.versionLabel.format(currentVersionName(context)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when (val s = state) {
                        is AppUpdateRepository.UpdateState.Idle -> {
                            Button(onClick = { AppUpdateRepository.checkForUpdate(context) }) {
                                Text(strings.checkForUpdates)
                            }
                        }
                        is AppUpdateRepository.UpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(strings.checkingForUpdates)
                            }
                        }
                        is AppUpdateRepository.UpdateState.UpToDate -> {
                            Text(strings.upToDate)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { AppUpdateRepository.checkForUpdate(context) }) {
                                Text(strings.checkForUpdates)
                            }
                        }
                        is AppUpdateRepository.UpdateState.Available -> {
                            Text(
                                strings.updateAvailable.format(s.release.tag_name),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val notes = s.release.body?.trim()
                            if (!notes.isNullOrBlank()) {
                                Text(strings.releaseNotesLabel, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 12,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        AppUpdateRepository.downloadAndInstall(
                                            context,
                                            s.release,
                                            s.asset,
                                        )
                                    },
                                ) {
                                    Text(strings.downloadAndInstall)
                                }
                                OutlinedButton(
                                    onClick = { AppUpdateRepository.skipRelease(context, s.release) },
                                ) {
                                    Text(strings.skipVersion)
                                }
                            }
                        }
                        is AppUpdateRepository.UpdateState.Downloading -> {
                            Text(strings.downloading.format(s.progressPercent))
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { s.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is AppUpdateRepository.UpdateState.AwaitingInstallPermission -> {
                            Text(strings.allowInstallPermissionHint)
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { AppUpdateRepository.openInstallPermissionSettings(context) },
                            ) {
                                Text(strings.allowInstallPermission)
                            }
                        }
                        is AppUpdateRepository.UpdateState.Installing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(strings.installing)
                            }
                        }
                        is AppUpdateRepository.UpdateState.Error -> {
                            Text(
                                strings.updateError.format(s.message),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { AppUpdateRepository.checkForUpdate(context) }) {
                                Text(strings.checkForUpdates)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun currentVersionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (t: Throwable) {
    "?"
}
