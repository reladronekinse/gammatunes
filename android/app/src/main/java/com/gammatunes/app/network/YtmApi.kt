package com.gammatunes.app.network

import com.gammatunes.app.model.AlbumTracksResponse
import com.gammatunes.app.model.Artist
import com.gammatunes.app.model.ArtistSearchResponse
import com.gammatunes.app.model.AuthLoginResponse
import com.gammatunes.app.model.AuthStatusResponse
import com.gammatunes.app.model.PlaylistTracksResponse
import com.gammatunes.app.model.PlaylistsResponse
import com.gammatunes.app.model.SearchResponse
import com.gammatunes.app.model.SimpleOkResponse
import com.gammatunes.app.model.StreamResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface YtmApi {

    @GET("search/artists")
    suspend fun searchArtists(@Query("q") query: String): ArtistSearchResponse

    @GET("search")
    suspend fun searchTracks(@Query("q") query: String): SearchResponse

    @GET("artists/{artistId}")
    suspend fun artistDetail(@Path("artistId") artistId: String): Artist

    @GET("albums/{albumId}")
    suspend fun albumTracks(@Path("albumId") albumId: String): AlbumTracksResponse

    @GET("stream/{videoId}")
    suspend fun stream(@Path("videoId") videoId: String): StreamResponse

    @GET("auth/status")
    suspend fun authStatus(): AuthStatusResponse

    @POST("auth/login")
    suspend fun authLogin(@Body body: Map<String, String>): AuthLoginResponse

    @POST("auth/logout")
    suspend fun authLogout(): SimpleOkResponse

    @GET("liked")
    suspend fun likedSongs(@Query("limit") limit: Int = 100): SearchResponse

    @GET("playlists")
    suspend fun libraryPlaylists(@Query("limit") limit: Int = 50): PlaylistsResponse

    @GET("playlists/{playlistId}")
    suspend fun playlistTracks(
        @Path("playlistId") playlistId: String,
        @Query("limit") limit: Int = 100,
    ): PlaylistTracksResponse

    @POST("rate")
    suspend fun rateSongBody(@Body body: Map<String, String>): SimpleOkResponse
}

suspend fun YtmApi.rateSong(videoId: String, rating: String): SimpleOkResponse =
    rateSongBody(mapOf("videoId" to videoId, "rating" to rating))
