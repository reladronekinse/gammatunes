package com.gammatunes.app.auth

import android.content.Context
import android.util.Log
import com.gammatunes.app.model.PlaylistSummary
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.network.addToPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import retrofit2.HttpException

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

    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists

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
            _statusMessage.value = extractErrorMessage(t)
            false
        } finally {
            _isBusy.value = false
        }
    }

    private fun extractErrorMessage(t: Throwable): String {
        if (t is HttpException) {
            val body = try {
                t.response()?.errorBody()?.string()
            } catch (_: Throwable) {
                null
            }

            if (!body.isNullOrBlank()) {
                val key = "\"detail\""
                val idx = body.indexOf(key)
                if (idx >= 0) {
                    val after = body.substring(idx + key.length)
                    val colon = after.indexOf(':')
                    val firstQuote = after.indexOf('"', startIndex = (colon + 1).coerceAtLeast(0))
                    val secondQuote = if (firstQuote >= 0) after.indexOf('"', startIndex = firstQuote + 1) else -1
                    if (firstQuote >= 0 && secondQuote > firstQuote) {
                        return decodeJsonString(after.substring(firstQuote + 1, secondQuote)).take(280)
                    }
                }

                val cleaned = decodeJsonString(
                    body.replace("\n", " ").replace(Regex("\\\\n"), " "),
                ).take(200)
                return cleaned
            }
            return "HTTP ${t.code()}"
        }
        return t.message ?: "Ошибка входа"
    }


    private fun decodeJsonString(raw: String): String {
        return buildString(raw.length) {
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        'u' -> {
                            if (i + 5 < raw.length) {
                                val hex = raw.substring(i + 2, i + 6)
                                val code = hex.toIntOrNull(16)
                                if (code != null) {
                                    append(code.toChar())
                                    i += 6
                                    continue
                                }
                            }
                        }
                        'n' -> { append('\n'); i += 2; continue }
                        't' -> { append('\t'); i += 2; continue }
                        '"' -> { append('"'); i += 2; continue }
                        '\\' -> { append('\\'); i += 2; continue }
                        '/' -> { append('/'); i += 2; continue }
                    }
                }
                append(c)
                i++
            }
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
            _playlists.value = emptyList()
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
            _statusMessage.value = extractErrorMessage(t)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun refreshPlaylists(limit: Int = 50) = withContext(Dispatchers.IO) {
        if (!_isLoggedIn.value) return@withContext
        _isBusy.value = true
        try {
            val response = ApiClient.api.libraryPlaylists(limit)
            _playlists.value = response.playlists
            _statusMessage.value = null
        } catch (t: Throwable) {
            Log.e(TAG, "playlists failed", t)
            _statusMessage.value = extractErrorMessage(t)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun loadPlaylistTracks(playlistId: String, limit: Int = 100): List<Track> =
        withContext(Dispatchers.IO) {
            try {
                ApiClient.api.playlistTracks(playlistId, limit).tracks
            } catch (t: Throwable) {
                Log.e(TAG, "playlist tracks failed", t)
                _statusMessage.value = extractErrorMessage(t)
                emptyList()
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

    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.addToPlaylist(playlistId, videoId)
            resp.ok
        } catch (t: Throwable) {
            Log.e(TAG, "addToPlaylist failed", t)
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
