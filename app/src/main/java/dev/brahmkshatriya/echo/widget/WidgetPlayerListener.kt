package dev.brahmkshatriya.echo.widget

import android.graphics.Bitmap
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import dev.brahmkshatriya.echo.playback.PlayerBridge.loadImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WidgetPlayerListener(
    private val update: (Bitmap?) -> Unit
) : Player.Listener {

    var controller: Player? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var result: Job? = null
    private var image: Bitmap? = null
    private fun getImage() {
        result?.cancel()
        result = scope.launch {
            image = controller?.loadImage()
            update(image)
        }
    }

    fun removed() {
        scope.cancel()
        result = null
        controller?.removeListener(this)
        controller = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        getImage()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        getImage()
        update(image)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        update(image)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        update(image)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        update(image)
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        update(image)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        update(image)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        update(image)
    }
}
