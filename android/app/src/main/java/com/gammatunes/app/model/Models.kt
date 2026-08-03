package com.gammatunes.app.model

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    /** browseId альбома/релиза — для перехода с плеера на страницу альбома. */
    val albumId: String? = null,
    val thumbnail: String? = null,
    val durationSeconds: Int? = null,
    /** browseId артиста из YTMusic — нужен для перехода с плеера на карточку. */
    val artistId: String? = null,
)

data class Album(
    val albumId: String,
    val title: String,
    val thumbnail: String? = null,
    val year: String? = null,
)

data class Artist(
    val artistId: String,
    val name: String,
    val thumbnail: String? = null,
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
)

// Результат поиска: карточки артистов (не треков). Каждый элемент здесь —
// "лёгкая" версия артиста без альбомов; полный список альбомов подгружается
// отдельным запросом (GET /artists/{artistId}) по тапу на карточку.
data class ArtistSearchResponse(
    val artists: List<Artist> = emptyList(),
)

// Результат поиска треков (песен) — используется вместе с ArtistSearchResponse:
// поиск теперь отдаёт и артистов, и отдельные треки, как в самом YouTube Music.
data class SearchResponse(
    val results: List<Track> = emptyList(),
)

data class AlbumTracksResponse(
    val albumId: String,
    val title: String,
    val thumbnail: String? = null,
    val tracks: List<Track> = emptyList(),
)

data class StreamResponse(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    // HTTP-заголовки (в первую очередь User-Agent), с которыми yt-dlp получил
    // эту ссылку. YouTube привязывает подписанную ссылку к заголовкам запроса:
    // если плеер запрашивает поток с другим User-Agent, соединение начинает
    // жёстко троттлиться и обрывается через несколько секунд после начала
    // воспроизведения. Поэтому эти заголовки нужно передать в ExoPlayer как
    // есть, а не полагаться на его заголовки по умолчанию.
    val httpHeaders: Map<String, String> = emptyMap(),
)

data class AuthStatusResponse(
    val loggedIn: Boolean = false,
    val accountName: String? = null,
)

data class AuthLoginResponse(
    val ok: Boolean = false,
    val accountName: String? = null,
    val authJson: String? = null,
    val detail: String? = null,
)

data class SimpleOkResponse(
    val ok: Boolean = false,
    val videoId: String? = null,
    val rating: String? = null,
    val detail: String? = null,
)


data class PlaylistSummary(
    val playlistId: String,
    val title: String,
    val thumbnail: String? = null,
    val count: Int? = null,
)

data class PlaylistsResponse(
    val playlists: List<PlaylistSummary> = emptyList(),
)

data class PlaylistTracksResponse(
    val playlistId: String,
    val title: String,
    val tracks: List<Track> = emptyList(),
)
