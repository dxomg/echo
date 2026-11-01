package dev.brahmkshatriya.echo.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.os.bundleOf
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Rating
import com.google.android.exoplayer2.ThumbRating
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.get
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtension
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtensionOrThrow
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaItemUtils.track
import dev.brahmkshatriya.echo.playback.PlayerCommands.addToNextCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.addToQueueCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.imageCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.likeCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.playCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.radioCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.repeatCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.repeatOffCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.repeatOneCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.resumeCommand
import dev.brahmkshatriya.echo.playback.PlayerCommands.sleepTimer
import dev.brahmkshatriya.echo.playback.PlayerCommands.unlikeCommand
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverPlaylist
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverRepeat
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverShuffle
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverTracks
import dev.brahmkshatriya.echo.playback.exceptions.PlayerException
import dev.brahmkshatriya.echo.playback.listener.PlayerRadio
import dev.brahmkshatriya.echo.utils.CacheUtils.getFromCache
import dev.brahmkshatriya.echo.utils.Serializer.getSerialized
import dev.brahmkshatriya.echo.utils.Serializer.putSerialized
import dev.brahmkshatriya.echo.utils.image.ImageUtils.loadDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Session callback + shared queue logic, wired onto MediaSessionCompat.
 *
 * Exposes [sessionCallback] for the compat session and implements
 * [PlayerBridge.Handler] so UI in the same process can send the same
 * commands that used to go over the media3 controller surface.
 */
class PlayerCallback(
    private val player: Player,
    override val app: App,
    override val scope: CoroutineScope,
    private val throwableFlow: MutableSharedFlow<Throwable>,
    private val extensions: ExtensionLoader,
    private val radioFlow: MutableStateFlow<PlayerState.Radio>,
    override val downloadFlow: StateFlow<List<Downloader.Info>>,
) : AndroidAutoCallback(app, scope, extensions.music, downloadFlow), PlayerBridge.Handler {

    // ==================== in-process command bridge ====================

    override fun handleCustomCommand(player: Player, command: String, args: Bundle) {
        when (command) {
            likeCommand -> handleRating(player, ThumbRating(true))
            unlikeCommand -> handleRating(player, ThumbRating())
            repeatOffCommand -> setRepeat(player, Player.REPEAT_MODE_OFF)
            repeatOneCommand -> setRepeat(player, Player.REPEAT_MODE_ONE)
            repeatCommand -> setRepeat(player, Player.REPEAT_MODE_ALL)
            playCommand -> playItem(player, args)
            addToQueueCommand -> addToQueue(player, args)
            addToNextCommand -> addToNext(player, args)
            radioCommand -> radio(player, args)
            sleepTimer -> onSleepTimer(player, args.getLong("ms"))
            resumeCommand -> resume(player, args.getBoolean("cleared", true))
            imageCommand -> scope.launch { loadImage(player) }
        }
    }

    override fun handleRating(player: Player, rating: Rating) = scope.launch {
        if (rating !is ThumbRating) return@launch
        val item = player.with { currentMediaItem }
            ?: return@launch
        val track = item.track
        runCatching {
            val extension = extensions.music.getExtensionOrThrow(item.extensionId)
            extension.getAs<LikeClient, Unit> {
                likeItem(track, rating.isThumbsUp)
            }
        }.getOrElse {
            throwableFlow.emit(PlayerException(item, it))
            return@launch
        }
        val liked = rating.isThumbsUp
        val newItem = item.run {
            buildUpon().setMediaMetadata(
                mediaMetadata.buildUpon().setUserRating(ThumbRating(liked)).build()
            )
        }.build()
        player.with {
            replaceMediaItem(currentMediaItemIndex, newItem)
        }
    }

    override suspend fun loadImage(player: Player): Bitmap? {
        val item = player.with { currentMediaItem }
            ?: context.recoverPlaylist(app, downloadFlow.value, false).run {
                first.getOrNull(second)
            }
            ?: return null
        return item.track.cover.loadDrawable(context)?.toScaledBitmap(720)
    }

    private fun Drawable.toScaledBitmap(width: Int) = toBitmap().let { bmp ->
        val ratio = width.toFloat() / bmp.width
        val height = (bmp.height * ratio).toInt()
        bmp.scale(width, height)
    }

    // ==================== standard transport ====================

    val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            player.prepare()
            player.playWhenReady = true
        }

        override fun onPause() {
            player.playWhenReady = false
        }

        override fun onStop() {
            player.stop()
        }

        override fun onSkipToNext() {
            player.seekToNextMediaItem()
        }

        override fun onSkipToPrevious() {
            player.seekToPreviousMediaItem()
        }

        override fun onSeekTo(pos: Long) {
            player.seekTo(pos)
        }

        override fun onSetShuffleMode(mode: Int) {
            player.shuffleModeEnabled =
                mode != PlaybackStateCompat.SHUFFLE_MODE_NONE
        }

        override fun onSetRepeatMode(mode: Int) {
            player.repeatMode = when (mode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP ->
                    Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            player.setPlaybackSpeed(speed)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            handleCustomCommand(player, action ?: return, extras ?: Bundle.EMPTY)
        }

        override fun onSetRating(rating: android.media.Rating?) {
            if (rating?.ratingStyle == android.media.Rating.RATING_THUMB_UP && rating.isThumbUp)
                handleRating(player, ThumbRating(true))
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val id = mediaId ?: return
            scope.launch {
                if (!id.startsWith("auto/")) return@launch
                val (track, extId, _) =
                    context.getFromCache<Triple<Track, String, EchoMediaItem?>>(
                        id.substringAfter("auto/"), "auto"
                    ) ?: return@launch
                handleCustomCommand(player, playCommand, Bundle().apply {
                    putString("extId", extId)
                    putSerialized("item", track)
                    putBoolean("loaded", false)
                    putBoolean("shuffle", false)
                })
            }
        }
    }

    // ==================== shared command implementations ====================

    private fun resume(player: Player, withClear: Boolean) = scope.launch {
        withContext(Dispatchers.Main) {
            player.shuffleModeEnabled = context.recoverShuffle() == true
            player.repeatMode = context.recoverRepeat() ?: Player.REPEAT_MODE_OFF
        }
        val (items, index, pos) = context.recoverPlaylist(app, downloadFlow.value, withClear)
        withContext(Dispatchers.Main) {
            player.setMediaItems(items, index, pos)
            player.prepare()
        }
    }

    private var timerJob: Job? = null
    private fun onSleepTimer(player: Player, ms: Long) {
        timerJob?.cancel()
        if (ms == 0L) return
        val time = if (ms == Long.MAX_VALUE) player.run {
            duration - currentPosition
        } else ms

        timerJob = scope.launch {
            delay(time)
            player.with { pause() }
        }
    }

    private fun setRepeat(player: Player, repeat: Int) {
        player.repeatMode = repeat
    }

    private fun radio(player: Player, args: Bundle) = scope.launch {
        val extId = args.getString("extId") ?: return@launch
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@launch
        val itemLoaded = args.getBoolean("loaded", false)
        val extension = extensions.music.getExtension(extId) ?: return@launch
        val newItem = if (itemLoaded) item else loadItem(extension, item)
        radioFlow.value = PlayerState.Radio.Loading
        val loaded = PlayerRadio.start(
            throwableFlow, extension, newItem, null
        )
        if (loaded == null) return@launch
        player.with {
            clearMediaItems()
            shuffleModeEnabled = false
        }
        PlayerRadio.play(player, downloadFlow, app, radioFlow, loaded)
        player.with { play() }
    }

    private suspend fun loadItem(
        extension: Extension<*>, item: EchoMediaItem,
    ) = when (item) {
        is Track -> extension.getAs<TrackClient, EchoMediaItem> { loadTrack(item, false) }
        is Album -> extension.getAs<AlbumClient, EchoMediaItem> { loadAlbum(item) }
        is Playlist -> extension.getAs<PlaylistClient, EchoMediaItem> { loadPlaylist(item) }
        is Artist -> extension.getAs<ArtistClient, EchoMediaItem> { loadArtist(item) }
        is Radio -> throw IllegalStateException()
    }.getOrThrow()

    private suspend fun listTracks(
        extension: Extension<*>, item: EchoMediaItem, loaded: Boolean,
    ) = when (item) {
        is Album -> extension.getAs<AlbumClient, PagedData<Track>> {
            val album = if (!loaded) loadAlbum(item) else item
            loadTracks(album)?.pagedDataOfFirst()
                ?: PagedData.empty()
        }

        is Playlist -> extension.getAs<PlaylistClient, PagedData<Track>> {
            val playlist = if (!loaded) loadPlaylist(item) else item
            loadTracks(playlist).pagedDataOfFirst()
        }

        is Radio -> extension.getAs<RadioClient, PagedData<Track>> {
            val radio = if (!loaded) loadRadio(item) else item
            loadTracks(radio).pagedDataOfFirst()
        }

        is Artist -> extension.getAs<ArtistClient, PagedData<Track>> {
            val artist = if (!loaded) loadArtist(item) else item
            loadFeed(artist).pagedDataOfFirst().toTracks()
        }

        is Track -> Result.success(PagedData.Single { listOf(item) })
    }

    private fun playItem(player: Player, args: Bundle) = scope.launch {
        val extId = args.getString("extId") ?: return@launch
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@launch
        val loaded = args.getBoolean("loaded", false)
        val shuffle = args.getBoolean("shuffle", false)
        val extension = extensions.music.getExtension(extId) ?: return@launch
        when (item) {
            is Track -> {
                val mediaItem = MediaItemUtils.build(
                    app, downloadFlow.value, MediaState.Unloaded(extId, item), null
                )
                player.with {
                    setMediaItem(mediaItem)
                    prepare()
                    seekTo(item.playedDuration ?: 0)
                    play()
                }
            }

            else -> {
                val tracks = listTracks(extension, item, loaded).getOrElse {
                    throwableFlow.emit(it)
                    return@launch
                }

                val result = if (shuffle) extension.get { tracks.loadAll() }
                else runCatching {
                    val (list, continuation) = extension.get { tracks.loadPage(null) }.getOrThrow()
                    if (continuation != null) scope.launch {
                        val all = extension.get { tracks.loadAll() }.getOrElse {
                            throwableFlow.emit(it)
                            return@launch
                        }.drop(list.size).map {
                            MediaItemUtils.build(
                                app, downloadFlow.value, MediaState.Unloaded(extId, it), item
                            )
                        }
                        player.with { addMediaItems(list.size, all) }
                    }
                    list
                }
                val list = result.getOrElse {
                    throwableFlow.emit(it)
                    return@launch
                }
                player.with {
                    setMediaItems(list.map {
                        MediaItemUtils.build(
                            app, downloadFlow.value, MediaState.Unloaded(extId, it), item
                        )
                    })
                    shuffleModeEnabled = shuffle
                    seekTo(0, list.firstOrNull()?.playedDuration ?: 0)
                    play()
                }
            }
        }
    }

    private suspend fun <T> Player.with(block: suspend Player.() -> T): T =
        withContext(Dispatchers.Main) { block() }

    private suspend fun <T : Any> PagedData<T>.load(
        pages: Int = 5,
    ) = runCatching {
        val list = mutableListOf<T>()
        var page = loadPage(null)
        list.addAll(page.data)
        var count = 0
        while (page.continuation != null && count < pages) {
            page = loadPage(page.continuation)
            list.addAll(page.data)
            count++
        }
        list
    }

    private fun addToQueue(player: Player, args: Bundle) = scope.launch {
        val extId = args.getString("extId") ?: return@launch
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@launch
        val loaded = args.getBoolean("loaded", false)
        val extension = extensions.music.getExtension(extId) ?: return@launch
        val tracks = listTracks(extension, item, loaded).getOrElse {
            throwableFlow.emit(it)
            return@launch
        }.load().getOrElse {
            throwableFlow.emit(it)
            return@launch
        }
        if (tracks.isEmpty()) return@launch
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(extId, track),
                null
            )
        }
        player.with {
            addMediaItems(mediaItems)
            prepare()
        }
    }

    private var next = 0
    private var nextJob: Job? = null
    private fun addToNext(player: Player, args: Bundle) = scope.launch {
        val extId = args.getString("extId") ?: return@launch
        val item = args.getSerialized<EchoMediaItem>("item")?.getOrNull() ?: return@launch
        val loaded = args.getBoolean("loaded", false)
        val extension = extensions.music.getExtension(extId) ?: return@launch
        nextJob?.cancel()
        val tracks = listTracks(extension, item, loaded).getOrElse {
            throwableFlow.emit(it)
            return@launch
        }.load().getOrElse {
            throwableFlow.emit(it)
            return@launch
        }
        if (tracks.isEmpty()) return@launch
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(extId, track),
                null
            )
        }
        player.with {
            if (mediaItemCount == 0) playWhenReady = true
            addMediaItems(currentMediaItemIndex + 1 + next, mediaItems)
            prepare()
        }
        next += mediaItems.size
        nextJob = scope.launch {
            delay(5000)
            next = 0
        }
    }

    @Suppress("DEPRECATION")
    class ButtonReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val empty = context.recoverTracks().isNullOrEmpty()
            if (empty) {
                Toast.makeText(
                    context,
                    context.getString(R.string.no_last_played_track_found),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val service = Intent(context, PlayerService::class.java).apply {
                action = Intent.ACTION_MEDIA_BUTTON
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    intent.getParcelableExtra<android.view.KeyEvent?>(Intent.EXTRA_KEY_EVENT)
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(service)
            else context.startService(service)
        }
    }

    companion object {
        fun PagedData<Shelf>.toTracks() = map {
            it.getOrThrow().mapNotNull { shelf ->
                when (shelf) {
                    is Shelf.Category -> null
                    is Shelf.Lists.Categories -> null
                    is Shelf.Item -> listOfNotNull(shelf.media as? Track)
                    is Shelf.Lists.Items -> shelf.list.filterIsInstance<Track>()
                    is Shelf.Lists.Tracks -> shelf.list
                }
            }.flatten()
        }
    }
}
