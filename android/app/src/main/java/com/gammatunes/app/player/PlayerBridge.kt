package com.gammatunes.app.player

import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicReference


object PlayerBridge {

    private val playerRef = AtomicReference<ExoPlayer?>(null)

    @Volatile private var seekNext: (() -> Unit)? = null
    @Volatile private var seekPrevious: (() -> Unit)? = null
    @Volatile private var hasNextProvider: (() -> Boolean)? = null
    @Volatile private var hasPreviousProvider: (() -> Boolean)? = null

    val player: ExoPlayer?
        get() = playerRef.get()

    fun attachPlayer(player: ExoPlayer) {
        playerRef.set(player)
    }

    fun detachPlayer() {
        playerRef.set(null)
    }

    fun bindControls(
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        hasNext: () -> Boolean,
        hasPrevious: () -> Boolean,
    ) {
        seekNext = onNext
        seekPrevious = onPrevious
        hasNextProvider = hasNext
        hasPreviousProvider = hasPrevious
    }

    fun unbindControls() {
        seekNext = null
        seekPrevious = null
        hasNextProvider = null
        hasPreviousProvider = null
    }

    fun onSeekNext() {
        seekNext?.invoke()
    }

    fun onSeekPrevious() {
        seekPrevious?.invoke()
    }

    fun hasNext(): Boolean = hasNextProvider?.invoke() == true

    fun hasPrevious(): Boolean = hasPreviousProvider?.invoke() == true
}

