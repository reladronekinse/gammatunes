package com.gammatunes.app

import android.app.Application
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.backend.LocalBackend
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.i18n.LocaleRepository
import com.gammatunes.app.player.PlayHistoryRepository
import com.gammatunes.app.ui.settings.UiSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GammaTunesApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LocalBackend.start(this)
        OfflineRepository.init(this)
        PlayHistoryRepository.init(this)
        LocaleRepository.init(this)
        UiSettingsRepository.init(this)
        AuthRepository.init(this)

        appScope.launch {
            if (LocalBackend.awaitReady(timeoutMs = 45_000)) {
                AuthRepository.restoreSessionIfNeeded()
            }
        }
    }
}
