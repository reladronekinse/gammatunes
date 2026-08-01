package com.gammatunes.app.auth

import android.content.Context
import android.util.Log
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File


object AuthRepository {
    private const val TAG = "AuthRepository"
    private const val AUTH_FILE = "ytm_browser_auth.json"

    private lateinit var appContext: Context

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _accountHint = MutableStateFlow<String?>(null)
    val accountHint: StateFlow<String?> = _accountHint

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks: StateFlow<List<Track>> = _likedTracks

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    fun init(context: Context) {
        appContext = context.applicationContext
        val file = authFile()
        if (file.exists() && file.length() > 10) {
            _isLoggedIn.value = true
            _accountHint.value = "Сессия сохранена"
        }
    }

    private fun authFile(): File = File(appContext.filesDir, AUTH_FILE)


    suspend fun loginWithHeaders(rawHeaders: String): Boolean = withContext(Dispatchers.IO) {
        _isBusy.value = true
        _statusMessage.value = null
        try {
            val response = ApiClient.api.authLogin(mapOf("headersRaw" to rawHeaders.trim()))
            if (response.ok) {


                authFile().writeText(response.authJson ?: rawHeaders)
                _isLoggedIn.value = true
                _accountHint.value = response.accountName ?: "Вход выполнен"
                _statusMessage.value = "Вход выполнен"
                true
            } else {
                _statusMessage.value = response.detail ?: "Не удалось войти"
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "login failed", t)
            _statusMessage.value = t.message ?: "Ошибка входа"
            false
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        _isBusy.value = true
        try {
            runCatching { ApiClient.api.authLogout() }
            authFile().delete()
            _isLoggedIn.value = false
            _accountHint.value = null
            _likedTracks.value = emptyList()
            _statusMessage.value = "Вы вышли из аккаунта"
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun refreshLiked(limit: Int = 100) = withContext(Dispatchers.IO) {
        if (!_isLoggedIn.value) return@withContext
        _isBusy.value = true
        _statusMessage.value = null
        try {
            val response = ApiClient.api.likedSongs(limit)
            _likedTracks.value = response.results
            if (response.results.isEmpty()) {
                _statusMessage.value = "Лайкнутых треков нет или сессия устарела"
            }
        } catch (t: Throwable) {
            Log.e(TAG, "liked songs failed", t)
            _statusMessage.value = t.message ?: "Не удалось загрузить лайки"
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun likeTrack(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.api.rateSongBody(mapOf("videoId" to videoId, "rating" to "LIKE"))
            response.ok
        } catch (t: Throwable) {
            Log.e(TAG, "like failed", t)
            false
        }
    }

    suspend fun unlikeTrack(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.api.rateSongBody(mapOf("videoId" to videoId, "rating" to "INDIFFERENT"))
            response.ok
        } catch (t: Throwable) {
            Log.e(TAG, "unlike failed", t)
            false
        }
    }


    suspend fun restoreSessionIfNeeded() = withContext(Dispatchers.IO) {
        val file = authFile()
        if (!file.exists()) return@withContext
        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext
            val response = ApiClient.api.authLogin(mapOf("headersRaw" to content))
            if (response.ok) {
                _isLoggedIn.value = true
                _accountHint.value = response.accountName ?: "Сессия восстановлена"
            } else {
                _isLoggedIn.value = false
                file.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "restore session failed: ${t.message}")
        }
    }
}

