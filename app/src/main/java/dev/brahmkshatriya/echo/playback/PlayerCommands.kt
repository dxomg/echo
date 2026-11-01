package dev.brahmkshatriya.echo.playback

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.playback.MediaItemUtils.isLiked

object PlayerCommands {
    // Plain string command ids, carried over custom media-session commands.
    val likeCommand = "liked"
    val unlikeCommand = "unliked"
    val repeatCommand = "repeat"
    val repeatOffCommand = "repeat_off"
    val repeatOneCommand = "repeat_one"
    val playCommand = "play"
    val addToQueueCommand = "add_to_queue"
    val addToNextCommand = "add_to_next"
    val radioCommand = "radio"
    val sleepTimer = "sleep_timer"
    val resumeCommand = "resume"
    val imageCommand = "image"

    /** A long "timer never fires" marker preserved for the sleep-timer API. */
    const val NEVER_FIRE_MS = Long.MAX_VALUE

    fun allCommandIds() = setOf(
        likeCommand, unlikeCommand, repeatCommand, repeatOffCommand, repeatOneCommand,
        playCommand, addToQueueCommand, addToNextCommand, radioCommand, sleepTimer,
        resumeCommand, imageCommand
    )

    /**
     * Pre-21 platforms have no session custom layout; the like/repeat buttons ride the
     * notification through [PlaybackStateCompat.CustomAction] slots instead.
     */
    fun customActions(context: Context, item: MediaItem?, repeat: Int): List<PlaybackStateCompat.CustomAction> {
        val list = mutableListOf<PlaybackStateCompat.CustomAction>()
        val repeatAction = when (repeat) {
            Player.REPEAT_MODE_ONE -> Triple(
                R.drawable.ic_repeat_one_20dp, R.string.repeat_one,
                repeatOffCommand,
            )

            Player.REPEAT_MODE_OFF -> Triple(
                R.drawable.ic_repeat_20dp, R.string.repeat_off,
                repeatCommand,
            )

            else -> Triple(
                R.drawable.ic_repeat_on_20dp, R.string.repeat_all,
                repeatOneCommand,
            )
        }
        list.add(
            PlaybackStateCompat.CustomAction.Builder(
                repeatAction.third, context.getString(repeatAction.second),
                repeatAction.first
            ).build()
        )
        if (item != null) {
            val (resId, label, action) = if (!item.isLiked) {
                Triple(R.drawable.ic_favorite_20dp, R.string.like, likeCommand)
            } else {
                Triple(R.drawable.ic_favorite_filled_20dp, R.string.unlike, unlikeCommand)
            }
            list.add(
                PlaybackStateCompat.CustomAction.Builder(action, context.getString(label), resId)
                    .build()
            )
        }
        return list
    }
}
