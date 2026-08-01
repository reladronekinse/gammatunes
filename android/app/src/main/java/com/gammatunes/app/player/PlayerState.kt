package com.gammatunes.app.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gammatunes.app.model.Track
import com.gammatunes.app.network.ApiClient
import com.gammatunes.app.offline.OfflineRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


enum class RepeatMode { OFF, ALL, ONE }


@UnstableApi
class PlayerState(private val context: Context, private val scope: CoroutineScope) {

    private var boundPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    var currentTrack by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoadingStream by mutableStateOf(false)
        private set
    var streamError by mutableStateOf<String?>(null)
        private set
    var repeatMode by mutableStateOf(RepeatMode.OFF)
        private set

    var currentPositionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var isSeeking by mutableStateOf(false)

    fun seekTo(positionMs: Long) {
        boundPlayer?.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = -1

    val hasNext: Boolean
        get() = queueIndex in 0 until queue.size - 1
    val hasPrevious: Boolean
        get() = queueIndex > 0 && queue.isNotEmpty()

    init {
        PlayerBridge.bindControls(
            onNext = { playNext() },
            onPrevious = { playPrevious() },
            hasNext = { hasNext },
            hasPrevious = { hasPrevious },
        )
        connectMediaSession()
        startPositionPolling()
    }

    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                delay(500)
                val player = boundPlayer ?: continue
                if (isSeeking) continue
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                durationMs = if (d != androidx.media3.common.C.TIME_UNSET && d > 0) d else 0L
            }
        }
    }

    private fun connectMediaSession() {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    PlayerBridge.player?.let { attachToPlayer(it) }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun attachToPlayer(player: ExoPlayer) {
        if (boundPlayer === player) return
        playerListener?.let { boundPlayer?.removeListener(it) }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            player.seekTo(0)
                            player.play()
                        }
                        RepeatMode.ALL -> {
                            if (hasNext) {
                                playNext()
                            } else if (queue.isNotEmpty()) {
                                queueIndex = 0
                                loadAndPlay(queue[queueIndex])
                            }
                        }
                        RepeatMode.OFF -> playNext()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val track = currentTrack
                if (track != null && !retryAttempted) {
                    val position = player.currentPosition
                    loadAndPlay(track, resumePositionMs = position, isRetry = true)
                } else {
                    streamError = error.message ?: "Ошибка воспроизведения"
                }
            }
        }
        player.addListener(listener)
        boundPlayer = player
        playerListener = listener
        isPlaying = player.isPlaying
    }

    fun play(track: Track, queue: List<Track> = listOf(track)) {
        this.queue = queue
        this.queueIndex = queue.indexOfFirst { it.videoId == track.videoId }.let {
            if (it >= 0) it else 0
        }
        if (track.videoId == currentTrack?.videoId) {
            togglePlayPause()
            return
        }
        loadAndPlay(track)
    }

    fun playNext() {
        if (!hasNext) return
        queueIndex++
        loadAndPlay(queue[queueIndex])
    }

    fun playPrevious() {
        if (!hasPrevious) return
        queueIndex--
        loadAndPlay(queue[queueIndex])
    }

    private var retryAttempted = false

    private fun mediaItemFor(track: Track, uri: android.net.Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.thumbnail?.let { android.net.Uri.parse(it) })
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun loadAndPlay(track: Track, resumePositionMs: Long = 0L, isRetry: Boolean = false) {
        retryAttempted = if (isRetry) {
            if (retryAttempted) return
            true
        } else {
            false
        }
        currentTrack = track
        streamError = null
        currentPositionMs = resumePositionMs
        durationMs = 0L

        val offlineTrack = OfflineRepository.localTrack(track.videoId)
        if (offlineTrack != null) {
            isLoadingStream = false
            scope.launch {
                val player = awaitPlayer() ?: run {
                    streamError = "Плеер не готов"
                    return@launch
                }
                try {
                    val uri = android.net.Uri.fromFile(java.io.File(offlineTrack.filePath))
                    player.setMediaItem(mediaItemFor(track, uri), resumePositionMs)
                    player.prepare()
                    player.playWhenReady = true
                } catch (e: Exception) {
                    streamError = e.message ?: "Не удалось воспроизвести скачанный трек"
                }
            }
            return
        }

        isLoadingStream = true
        scope.launch {
            try {
                val stream = ApiClient.api.stream(track.videoId)
                val player = awaitPlayer() ?: run {
                    streamError = "Плеер не готов"
                    return@launch
                }
                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    setAllowCrossProtocolRedirects(true)
                    setConnectTimeoutMs(20_000)
                    setReadTimeoutMs(20_000)
                }
                val item = mediaItemFor(track, android.net.Uri.parse(stream.streamUrl))
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(item)
                player.setMediaSource(mediaSource, resumePositionMs)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                streamError = e.message ?: "Не удалось получить аудиопоток"
            } finally {
                isLoadingStream = false
            }
        }
    }

    private suspend fun awaitPlayer(): ExoPlayer? {
        PlayerBridge.player?.let {
            attachToPlayer(it)
            return it
        }
        if (mediaController == null && controllerFuture != null) {
            runCatching {
                suspendCancellableCoroutine { cont ->
                    val f = controllerFuture!!
                    f.addListener(
                        {
                            runCatching { f.get() }.onSuccess { mediaController = it }
                            if (cont.isActive) cont.resume(Unit)
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
            }
        }
        repeat(50) {
            PlayerBridge.player?.let {
                attachToPlayer(it)
                return it
            }
            delay(100)
        }
        return PlayerBridge.player?.also { attachToPlayer(it) }
    }

    fun togglePlayPause() {
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun release() {
        playerListener?.let { boundPlayer?.removeListener(it) }
        playerListener = null
        boundPlayer = null
        PlayerBridge.unbindControls()
        mediaController?.release()
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerState(): PlayerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PlayerState(context.applicationContext, scope) }
    DisposableEffect(Unit) {
        onDispose { state.release() }
    }
    return state
}

