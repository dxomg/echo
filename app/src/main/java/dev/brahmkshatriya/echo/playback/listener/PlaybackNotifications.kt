package dev.brahmkshatriya.echo.playback.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.isClient
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaSessionMapping.toCompatMetadata
import dev.brahmkshatriya.echo.playback.PlayerCommands
import dev.brahmkshatriya.echo.playback.PlayerService
import dev.brahmkshatriya.echo.playback.renderer.PlayerBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlaybackChannels {
    const val CHANNEL_ID = "echo_notification_channel_id"
    const val NOTIFICATION_ID = 1

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}

/**
 * Renders the media notification (MediaStyle) and keeps the foreground
 * state in sync with playback, matching the original
 * DefaultMediaNotificationProvider behavior.
 */
class PlayerNotifications(
    private val context: Context,
    private val service: PlayerService,
    private val session: MediaSessionCompat,
    private val loader: PlayerBitmapLoader,
) {

    private var item: MediaItem? = null
    private var isPlaying = false
    private var artwork: Bitmap? = null
    private var published = false

    private val scope = CoroutineScope(Dispatchers.IO)

    fun update(player: Player, newItem: MediaItem?, newIsPlaying: Boolean) {
        val metadataChanged = newItem != item
        item = newItem
        isPlaying = newIsPlaying

        if (metadataChanged) loadArtwork(newItem)

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        PlaybackChannels.ensure(context)
        val cancelNotification =
            newItem == null && player.playbackState == Player.STATE_IDLE
        val notification = build(context, item, isPlaying, artwork)

        when {
            cancelNotification -> {
                service.stopForeground(true)
                runCatching { notificationManagerCompat.cancel(NOTIFICATION_ID) }
                published = false
                artwork = null
            }

            isPlaying -> {
                runCatching { service.startForeground(NOTIFICATION_ID, notification) }
                published = true
            }

            published -> {
                service.stopForeground(false)
                runCatching { notificationManagerCompat.notify(NOTIFICATION_ID, notification) }
            }
        }
    }

    private fun loadArtwork(newItem: MediaItem?) {
        artwork = null
        val uri = newItem?.mediaMetadata?.artworkUri ?: return
        scope.launch {
            val bitmap = runCatching { loader.loadBitmap(uri) }.getOrNull()
            if (bitmap == null) return@launch
            artwork = bitmap
            val notification = build(context, item, isPlaying, bitmap)
            if (isPlaying) runCatching {
                service.startForeground(NOTIFICATION_ID, notification)
            } else if (published) runCatching {
                service.stopForeground(false)
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun build(
        context: Context,
        item: MediaItem?,
        isPlaying: Boolean,
        artwork: Bitmap?,
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, PlaybackChannels.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mono)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(PlayerService.getPendingIntent(context))
        val metadata = item?.mediaMetadata
        if (metadata == null) {
            builder.setContentTitle(context.getString(R.string.app_name))
        } else {
            builder
                .setContentTitle(metadata.title?.toString() ?: context.getString(R.string.app_name))
                .setContentText(metadata.artist?.toString())
            if (artwork != null) builder.setLargeIcon(artwork)
        }

        builder.addAction(
            R.drawable.ic_skip_previous_40dp, "",
            PlayerService.getControlPendingIntent(context, PlayerService.ACTION_SKIP_TO_PREVIOUS)
        )
        builder.addAction(
            if (isPlaying) R.drawable.ic_pause_48dp else R.drawable.ic_play_48dp, "",
            PlayerService.getControlPendingIntent(context, PlayerService.ACTION_PLAY_PAUSE)
        )
        builder.addAction(
            R.drawable.ic_skip_next_40dp, "",
            PlayerService.getControlPendingIntent(context, PlayerService.ACTION_SKIP_TO_NEXT)
        )
        val repeatMode = session.controller.repeatMode
        PlayerCommands.customActions(context, item, repeatMode).forEach { action ->
            builder.addAction(
                action.icon,
                action.name,
                PlayerService.getControlPendingIntent(context, action.action)
            )
        }
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return builder
    }
}

/**
 * Publishes PlaybackStateCompat (transport actions, position, custom
 * like/repeat buttons) onto the compat session and keeps the notification
 * updated, mirroring the old setCustomLayout path.
 */
class PlaybackPublisher(
    private val context: Context,
    private val player: Player,
    private val session: MediaSessionCompat,
    private val notifications: PlayerNotifications,
    private val extensions: ExtensionLoader,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var supportsLike = false
    private var isPlaying = false
    private var tick = false

    private val positionTicker = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            publishState(player, supportsLike)
            if (tick && isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }

    private fun refresh(player: Player) {
        isPlaying = player.isPlaying
        scope.launch(Dispatchers.Main) {
            val item = player.currentMediaItem
            val liked = withContext(Dispatchers.IO) {
                item?.extensionId?.let {
                    extensions.music.getExtension(it)?.isClient<LikeClient>() ?: false
                } ?: false
            }
            supportsLike = liked
            publishState(player, liked)
        }
        notifications.update(player, player.currentMediaItem, isPlaying)
        restartTicker()
    }

    private fun restartTicker() {
        tick = isPlaying || player.isPlaying
        if (player.isPlaying) {
            handler.removeCallbacks(positionTicker)
            handler.postDelayed(positionTicker, TICK_MS)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        refresh(player)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        refresh(player)
    }

    override fun onTimelineChanged(timeline: com.google.android.exoplayer2.Timeline, reason: Int) {
        refresh(player)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        refresh(player)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        refresh(player)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        refresh(player)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        refresh(player)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
    ) {
        handler.removeCallbacks(positionTicker)
        publishState(player, supportsLike)
        restartTicker()
    }

    private fun publishState(player: Player, supportsLike: Boolean) {
        val item = player.currentMediaItem
        val isPlayingNow = player.isPlaying
        val playbackState = when (player.playbackState) {
            Player.STATE_IDLE, Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            else ->
                if (isPlayingNow) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED
        }
        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        if (player.hasNextMediaItem()) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        if (player.currentMediaItemIndex > 0)
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (item != null)
            actions = actions or PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_FAST_FORWARD

        val builder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                playbackState,
                if (playbackState == PlaybackStateCompat.STATE_STOPPED) 0
                else player.currentPosition,
                if (isPlayingNow) 1f else 0f
            )
            .setBufferedPosition(player.bufferedPosition)
        if (item != null) builder.setCustomActions(
            PlayerCommands.customActions(
                context, item.takeIf { supportsLike }, player.repeatMode
            )
        )
        session.setPlaybackState(builder.build())
        if (item != null) session.setMetadata(
            MediaSessionMapping.toCompatMetadata(item, player.duration)
        )
    }

    private companion object {
        const val TICK_MS = 5000L
    }
}
