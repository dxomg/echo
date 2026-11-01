package dev.brahmkshatriya.echo.playback.listener

import android.content.Context
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.playback.MediaItemUtils
import dev.brahmkshatriya.echo.playback.MediaItemUtils.isLoaded
import dev.brahmkshatriya.echo.playback.MediaItemUtils.retries
import dev.brahmkshatriya.echo.playback.PlayerState
import dev.brahmkshatriya.echo.playback.ResumptionUtils
import dev.brahmkshatriya.echo.playback.exceptions.PlayerException
import dev.brahmkshatriya.echo.utils.Serializer.rootCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class PlayerEventListener(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: Player,
    private val currentFlow: MutableStateFlow<PlayerState.Current?>,
    private val extensions: ExtensionLoader,
    private val throwableFlow: MutableSharedFlow<Throwable>
) : Player.Listener {

    private fun updateCurrentFlow() {
        if (player.currentMediaItem == null && player.mediaItemCount > 0)
            throw Exception("This is possible")
        currentFlow.value = player.currentMediaItem?.let {
            val isPlaying = player.isPlaying && player.playbackState == Player.STATE_READY
            PlayerState.Current(player.currentMediaItemIndex, it, it.isLoaded, isPlaying)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateCurrentFlow()
        ResumptionUtils.saveIndex(context, player.currentMediaItemIndex)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        updateCurrentFlow()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updateCurrentFlow()
        scope.launch { ResumptionUtils.saveQueue(context, player) }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        ResumptionUtils.saveRepeat(context, repeatMode)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        ResumptionUtils.saveShuffle(context, shuffleModeEnabled)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateCurrentFlow()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateCurrentFlow()
        ResumptionUtils.saveCurrentPos(context, player.currentPosition)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        ResumptionUtils.saveCurrentPos(context, player.currentPosition)
    }

    private val maxRetries = 3
    private val maxSingleItemRetries = 1
    private var currentRetries = 0
    private var last: KClass<*>? = null
    override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause ?: error
        val mediaItem = player.currentMediaItem
        scope.launch { throwableFlow.emit(PlayerException(mediaItem, cause)) }

        val old = last
        last = cause.rootCause::class
        if (old != null && old == last) currentRetries++
        else currentRetries = 0

        if (mediaItem == null) return
        val index = player.currentMediaItemIndex
        val retries = mediaItem.retries

        if (currentRetries >= maxRetries) return
        if (retries >= maxSingleItemRetries) {
            val hasMore = index < player.mediaItemCount - 1
            if (!hasMore) return
            player.seekToNextMediaItem()
        } else {
            val newItem = MediaItemUtils.withRetry(mediaItem)
            player.replaceMediaItem(index, newItem)
        }
        player.prepare()
        player.play()
    }
}
