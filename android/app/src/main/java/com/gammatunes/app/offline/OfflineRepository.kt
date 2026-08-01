package com.gammatunes.app.offline

import android.content.Context
import android.util.Log
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class OfflineTrack(
    val track: Track,
    val filePath: String,
    val mimeType: String,
)


data class OfflineAlbum(
    val albumId: String,
    val title: String,
    val thumbnail: String? = null,
    val year: String? = null,
    val trackIds: List<String> = emptyList(),
)

object OfflineRepository {
    private const val TAG = "OfflineRepository"
    private const val INDEX_FILE = "offline_index.json"
    private const val ALBUMS_FILE = "offline_albums.json"
    private const val MAX_DOWNLOAD_BYTES = 80L * 1024L * 1024L

    private val gson = Gson()
    private lateinit var appContext: Context

    private val httpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    private val _index = MutableStateFlow<Map<String, OfflineTrack>>(emptyMap())
    val index: StateFlow<Map<String, OfflineTrack>> = _index

    private val _albums = MutableStateFlow<Map<String, OfflineAlbum>>(emptyMap())
    val albums: StateFlow<Map<String, OfflineAlbum>> = _albums

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds

    private val _downloadingAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingAlbumIds: StateFlow<Set<String>> = _downloadingAlbumIds

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors

    fun init(context: Context) {
        appContext = context.applicationContext
        loadIndexFromDisk()
        loadAlbumsFromDisk()
    }

    fun isDownloaded(videoId: String): Boolean = _index.value.containsKey(videoId)

    fun isAlbumDownloaded(albumId: String): Boolean = _albums.value.containsKey(albumId)

    fun localTrack(videoId: String): OfflineTrack? = _index.value[videoId]

    fun albumTracksOrdered(albumId: String): List<Track> {
        val album = _albums.value[albumId] ?: return emptyList()
        return album.trackIds.mapNotNull { id -> _index.value[id]?.track }
    }

    private fun offlineDir(): File =
        File(appContext.filesDir, "offline").apply { if (!exists()) mkdirs() }

    suspend fun download(track: Track) {
        val videoId = track.videoId
        if (isDownloaded(videoId) || _downloadingIds.value.contains(videoId)) return

        _errors.update { it - videoId }
        _downloadingIds.update { it + videoId }
        withContext(Dispatchers.IO) {
            try {
                downloadTrackFile(track)
            } catch (t: Throwable) {
                Log.e(TAG, "Не удалось скачать трек $videoId", t)
                _errors.update { it + (videoId to (t.message ?: "Не удалось скачать")) }
                cleanupPart(videoId)
            } finally {
                _downloadingIds.update { it - videoId }
            }
        }
    }

    suspend fun downloadAlbum(
        albumId: String,
        title: String,
        thumbnail: String?,
        year: String? = null,
        tracks: List<Track>,
    ) {
        if (tracks.isEmpty()) return
        if (_downloadingAlbumIds.value.contains(albumId)) return

        _downloadingAlbumIds.update { it + albumId }
        withContext(Dispatchers.IO) {
            try {
                val orderedIds = mutableListOf<String>()
                for (track in tracks) {
                    orderedIds.add(track.videoId)
                    if (!isDownloaded(track.videoId)) {
                        _downloadingIds.update { it + track.videoId }
                        try {
                            downloadTrackFile(track)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Альбом $albumId: трек ${track.videoId}", t)
                            _errors.update {
                                it + (track.videoId to (t.message ?: "Не удалось скачать"))
                            }
                            cleanupPart(track.videoId)
                        } finally {
                            _downloadingIds.update { it - track.videoId }
                        }
                    }
                }
                val offlineAlbum = OfflineAlbum(
                    albumId = albumId,
                    title = title,
                    thumbnail = thumbnail,
                    year = year,
                    trackIds = orderedIds,
                )
                _albums.update { it + (albumId to offlineAlbum) }
                persistAlbumsToDisk()
            } finally {
                _downloadingAlbumIds.update { it - albumId }
            }
        }
    }

    suspend fun deleteAlbum(albumId: String, deleteTrackFiles: Boolean = false) {
        withContext(Dispatchers.IO) {
            val album = _albums.value[albumId]
            _albums.update { it - albumId }
            persistAlbumsToDisk()
            if (deleteTrackFiles && album != null) {
                for (id in album.trackIds) {
                    val usedElsewhere = _albums.value.values.any { it.trackIds.contains(id) }
                    if (!usedElsewhere) {
                        _index.value[id]?.let { runCatching { File(it.filePath).delete() } }
                        _index.update { it - id }
                    }
                }
                persistIndexToDisk()
            }
        }
    }

    suspend fun delete(videoId: String) {
        withContext(Dispatchers.IO) {
            _index.value[videoId]?.let { offlineTrack ->
                runCatching { File(offlineTrack.filePath).delete() }
            }
            _index.update { it - videoId }
            persistIndexToDisk()
            var albumsChanged = false
            val updated = _albums.value.mapValues { (_, album) ->
                if (album.trackIds.contains(videoId)) {
                    albumsChanged = true
                    album.copy(trackIds = album.trackIds.filter { it != videoId })
                } else album
            }.filterValues { it.trackIds.isNotEmpty() }
            if (albumsChanged) {
                _albums.value = updated
                persistAlbumsToDisk()
            }
        }
    }

    private suspend fun downloadTrackFile(track: Track) {
        val videoId = track.videoId
        val stream = ApiClient.api.stream(videoId)
        val extension = extensionFor(stream.mimeType)
        val outFile = File(offlineDir(), "$videoId.$extension")
        val tmpFile = File(offlineDir(), "$videoId.$extension.part")

        val request = Request.Builder()
            .url(stream.streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("Пустой ответ")
            val contentLength = body.contentLength()
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw java.io.IOException("Файл слишком большой (${contentLength} байт)")
            }

            tmpFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw java.io.IOException("Превышен лимит размера скачивания")
                        }
                        output.write(buffer, 0, read)
                    }
                    if (total == 0L) {
                        throw java.io.IOException("Скачан пустой файл")
                    }
                }
            }
        }

        if (!tmpFile.renameTo(outFile)) {
            tmpFile.copyTo(outFile, overwrite = true)
            tmpFile.delete()
        }

        val offlineTrack = OfflineTrack(
            track = track,
            filePath = outFile.absolutePath,
            mimeType = stream.mimeType,
        )
        _index.update { it + (videoId to offlineTrack) }
        persistIndexToDisk()
    }

    private fun cleanupPart(videoId: String) {
        runCatching {
            offlineDir().listFiles()?.forEach { f ->
                if (f.name.startsWith(videoId) && f.name.endsWith(".part")) {
                    f.delete()
                }
            }
        }
    }

    private fun extensionFor(mimeType: String): String = when {
        mimeType.contains("webm", ignoreCase = true) -> "webm"
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "m4a"
        mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
        else -> "m4a"
    }

    private fun loadIndexFromDisk() {
        runCatching {
            val file = File(appContext.filesDir, INDEX_FILE)
            if (!file.exists()) return
            val json = file.readText()
            val type = object : TypeToken<Map<String, OfflineTrack>>() {}.type
            val loaded: Map<String, OfflineTrack> = gson.fromJson(json, type) ?: emptyMap()
            _index.value = loaded.filterValues { File(it.filePath).exists() }
        }.onFailure { Log.e(TAG, "Не удалось прочитать offline_index.json", it) }
    }

    private fun persistIndexToDisk() {
        runCatching {
            File(appContext.filesDir, INDEX_FILE).writeText(gson.toJson(_index.value))
        }.onFailure { Log.e(TAG, "Не удалось сохранить offline_index.json", it) }
    }

    private fun loadAlbumsFromDisk() {
        runCatching {
            val file = File(appContext.filesDir, ALBUMS_FILE)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, OfflineAlbum>>() {}.type
            val loaded: Map<String, OfflineAlbum> = gson.fromJson(file.readText(), type) ?: emptyMap()
            _albums.value = loaded
        }.onFailure { Log.e(TAG, "Не удалось прочитать offline_albums.json", it) }
    }

    private fun persistAlbumsToDisk() {
        runCatching {
            File(appContext.filesDir, ALBUMS_FILE).writeText(gson.toJson(_albums.value))
        }.onFailure { Log.e(TAG, "Не удалось сохранить offline_albums.json", it) }
    }
}

