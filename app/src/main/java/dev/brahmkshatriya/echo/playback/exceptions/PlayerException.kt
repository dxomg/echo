package dev.brahmkshatriya.echo.playback.exceptions

import com.google.android.exoplayer2.MediaItem

class PlayerException(
    val mediaItem: MediaItem?,
    override val cause: Throwable
) : Exception()