package com.gammatunes.app

import android.app.Application
import com.gammatunes.app.auth.AuthRepository
import com.gammatunes.app.backend.LocalBackend
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.i18n.LocaleRepository
import com.gammatunes.app.ui.settings.UiSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Точка входа процесса. Здесь (и только здесь — гарантированно один раз за
 * жизнь процесса) поднимаем встроенный Python-бэкенд, чтобы он был готов
 * ещё до того, как откроется первый экран, и загружаем индекс скачанных
 * оффлайн-треков с диска.
 */
class GammaTunesApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LocalBackend.start(this)
        OfflineRepository.init(this)
        LocaleRepository.init(this)
        UiSettingsRepository.init(this)
        AuthRepository.init(this)
        // После готовности бэкенда восстановим browser-сессию, если она была.
        appScope.launch {
            if (LocalBackend.awaitReady(timeoutMs = 45_000)) {
                AuthRepository.restoreSessionIfNeeded()
            }
        }
    }
}
