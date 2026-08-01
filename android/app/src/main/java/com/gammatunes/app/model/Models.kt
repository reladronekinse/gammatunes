package com.gammatunes.app.model

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val thumbnail: String? = null,
    val durationSeconds: Int? = null,

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
)


data class ArtistSearchResponse(
    val artists: List<Artist> = emptyList(),
)


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

