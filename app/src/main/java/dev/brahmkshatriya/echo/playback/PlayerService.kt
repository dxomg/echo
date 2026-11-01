package dev.brahmkshatriya.echo.playback

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import dev.brahmkshatriya.echo.MainActivity.Companion.getMainActivity
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.extensionPrefId
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.prefs
import dev.brahmkshatriya.echo.playback.listener.AudioFocusListener
import dev.brahmkshatriya.echo.playback.listener.EffectsListener
import dev.brahmkshatriya.echo.playback.listener.PlaybackPublisher
import dev.brahmkshatriya.echo.playback.listener.PlayerEventListener
import dev.brahmkshatriya.echo.playback.listener.PlayerRadio
import dev.brahmkshatriya.echo.playback.listener.PlayerNotifications
import dev.brahmkshatriya.echo.playback.listener.TrackingListener
import dev.brahmkshatriya.echo.playback.renderer.PlayerBitmapLoader
import dev.brahmkshatriya.echo.playback.renderer.RenderersFactory
import dev.brahmkshatriya.echo.playback.source.StreamableMediaSource
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.android.ext.android.inject
import java.io.File

/**
 * The playback service. On the legacy (API 18) stack this is a
 * [MediaBrowserServiceCompat] with a MediaSessionCompat token. In-process
 * consumers (UI/widgets) get the [Player] directly through the local binder;
 * external clients (Android Auto, Bluetooth, system) browse/queue through the
 * compat session surface, whose state is published by [PlaybackPublisher].
 */
class PlayerService : MediaBrowserServiceCompat() {

    private val extensionLoader by inject<ExtensionLoader>()
    private val extensions by lazy { extensionLoader }
    private val exoPlayerLazy: Lazy<ExoPlayer> = lazy { createExoplayer() }
    private val exoPlayer: ExoPlayer get() = exoPlayerLazy.value

    private val app by inject<App>()
    private val state by inject<PlayerState>()
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("PlayerService")

    private var mediaSession: MediaSessionCompat? = null
    private var player: Player? = null
    private lateinit var callback: PlayerCallback
    private lateinit var publisher: PlaybackPublisher

    private val effects by lazy { EffectsListener(exoPlayer, this, state.session) }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == SKIP_SILENCE) scope.launch(Dispatchers.Main) {
            exoPlayer.skipSilenceEnabled = prefs.getBoolean(key, true)
        }
    }

    private val cache by inject<SimpleCache>()

    private val mediaChangeFlow = MutableSharedFlow<Pair<MediaItem, MediaItem>>()

    override fun onCreate() {
        super.onCreate()
        val player = ShufflePlayer(exoPlayer)
        this.player = player

        scope.launch(Dispatchers.Main) {
            mediaChangeFlow.collect { (o, n) -> player.onMediaItemChanged(o, n) }
        }

        callback = PlayerCallback(
            player, app, scope, app.throwFlow, extensions, state.radio, downloadFlow
        )
        PlayerBridge.set(callback)

        val session = MediaSessionCompat(this, "Echo").apply {
            setCallback(callback.sessionCallback)
            setSessionActivity(getPendingIntent(this@PlayerService))
            isActive = true
        }
        mediaSession = session
        setSessionToken(session.sessionToken)

        val notifications = PlayerNotifications(
            this, this, session, PlayerBitmapLoader(this)
        )
        publisher = PlaybackPublisher(
            this, player, session, notifications, extensions, scope
        )

        player.addListener(AudioFocusListener(this, player))
        player.addListener(
            PlayerEventListener(this, scope, player, state.current, extensions, app.throwFlow)
        )
        player.addListener(publisher)
        player.addListener(
            PlayerRadio(
                app, scope, player, app.throwFlow, state.radio, extensions.music, downloadFlow
            )
        )
        player.addListener(
            TrackingListener(player, scope, extensions, state.current, app.throwFlow)
        )
        player.addListener(effects)
        app.settings.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        app.settings.unregisterOnSharedPreferenceChangeListener(listener)
        PlayerBridge.set(null)
        scope.cancel()
        if (exoPlayerLazy.isInitialized()) exoPlayer.release()
        mediaSession?.run {
            isActive = false
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // ==================== Browser (Android Auto etc.) ====================

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?,
    ): BrowserRoot = callback.root()

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) = callback.loadChildren(parentId, null, result)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) = callback.loadChildren(parentId, options, result)

    override fun onSearch(
        query: String,
        parameters: Bundle,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) = callback.search(query, result)

    // ==================== In-process binder (UI gets the Player) ====================

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) return super.onBind(intent)
        return binder
    }

    inner class LocalBinder : Binder() {
        fun player(): Player = this@PlayerService.player ?: exoPlayer
    }

    private val binder = LocalBinder()

    // ==================== started intents (notification / buttons) ====================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = this.player ?: return START_NOT_STICKY
        when (val action = intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
                val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return START_NOT_STICKY
                handleKeyEvent(player, event)
            }

            null -> Unit

            else -> handleAction(player, action)
        }
        return START_NOT_STICKY
    }

    private fun handleAction(player: Player, action: String) {
        when (action) {
            ACTION_PLAY -> playPlayer(player)
            ACTION_PAUSE -> player.pause()
            ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else playPlayer(player)
            ACTION_SKIP_TO_NEXT -> player.seekToNextMediaItem()
            ACTION_SKIP_TO_PREVIOUS -> player.seekToPreviousMediaItem()
            ACTION_STOP -> {
                player.stop()
                stopSelf()
            }

            else -> if (action in PlayerCommands.allCommandIds()) {
                callback.handleCustomCommand(player, action, Bundle.EMPTY)
            }
        }
    }

    private fun playPlayer(player: Player) {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.playWhenReady = true
    }

    private fun handleKeyEvent(player: Player, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> playPlayer(player)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> if (player.isPlaying) player.pause() else playPlayer(player)

            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            -> player.seekToNextMediaItem()

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> player.seekToPreviousMediaItem()

            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player.stop()
                stopSelf()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val stopPlayer = app.settings.getBoolean(CLOSE_PLAYER, false)
        val player = this.player ?: return stopSelf()
        if (stopPlayer || !player.isPlaying) stopSelf()
    }

    private val downloader by inject<Downloader>()
    private val downloadFlow by lazy { downloader.flow }

    private fun createExoplayer() = run {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val factory = StreamableMediaSource.Factory(
            app, scope, state, extensions, cache, downloadFlow, mediaChangeFlow
        )

        ExoPlayer.Builder(this, factory)
            .setRenderersFactory(RenderersFactory(this))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .also {
                it.skipSilenceEnabled = app.settings.getBoolean(SKIP_SILENCE, true)
            }
    }

    companion object {
        const val MORE_BRAIN_CAPACITY = "offload"
        const val CLOSE_PLAYER = "close_player"
        const val SKIP_SILENCE = "skip_silence"

        const val CACHE_SIZE = "cache_size"

        fun getCache(
            app: Application,
            settings: SharedPreferences,
        ): SimpleCache {
            val databaseProvider = StandaloneDatabaseProvider(app)
            val cacheSize = settings.getInt(CACHE_SIZE, 250)
            return SimpleCache(
                File(app.cacheDir, "exo-player"),
                LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L),
                databaseProvider
            )
        }

        const val STREAM_QUALITY = "stream_quality"
        const val UNMETERED_STREAM_QUALITY = "unmetered_stream_quality"
        val streamQualities = arrayOf("highest", "medium", "lowest")

        fun selectServerIndex(
            app: App,
            extensionId: String,
            streamables: List<Streamable>,
            downloaded: List<String>,
        ) = if (downloaded.isNotEmpty()) streamables.size
        else if (streamables.isNotEmpty()) {
            val streamable = streamables.select(app, extensionId) { it.quality }
            streamables.indexOf(streamable)
        } else -1

        private fun <E> List<E>.select(
            app: App,
            settings: SharedPreferences,
            quality: (E) -> Int,
            default: String = streamQualities[1],
        ): E? {
            val unmetered = if (app.isUnmetered) selectQuality(
                settings.getString(UNMETERED_STREAM_QUALITY, "off"),
                quality
            ) else null
            return unmetered ?: selectQuality(
                settings.getString(STREAM_QUALITY, default),
                quality
            )
        }

        private fun <E> List<E>.selectQuality(final: String?, quality: (E) -> Int): E? {
            return when (final) {
                streamQualities[0] -> maxBy { quality(it) }
                streamQualities[1] -> sortedBy { quality(it) }[size / 2]
                streamQualities[2] -> minBy { quality(it) }
                else -> null
            }
        }


        fun <T> List<T>.select(
            app: App, extensionId: String, quality: (T) -> Int,
        ): T {
            val extSettings =
                extensionPrefId(ExtensionType.MUSIC.name, extensionId).prefs(app.context)
            return select(app, extSettings, quality, "off")
                ?: select(app, app.settings, quality)
                ?: first()
        }

        const val ACTION_PLAY = "dev.brahmkshatriya.echo.play"
        const val ACTION_PAUSE = "dev.brahmkshatriya.echo.pause"
        const val ACTION_PLAY_PAUSE = "dev.brahmkshatriya.echo.play_pause"
        const val ACTION_SKIP_TO_NEXT = "dev.brahmkshatriya.echo.skip_next"
        const val ACTION_SKIP_TO_PREVIOUS = "dev.brahmkshatriya.echo.skip_previous"
        const val ACTION_STOP = "dev.brahmkshatriya.echo.stop"
        const val ACTION_BIND_PLAYER = "dev.brahmkshatriya.echo.bind_player"

        private val FLAGS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
            else 0

        /**
         * Same-process access to the service Player. Binds to the local
         * binder; the returned lambda unbinds. Matches the old
         * `MediaController` based [getController] signature so callers
         * (PlayerViewModel, widgets) keep working with a type swap.
         */
        fun getController(
            context: Application,
            block: (Player) -> Unit,
        ): () -> Unit {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service is PlayerService.LocalBinder) block(service.player())
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            val intent = Intent(context, PlayerService::class.java).apply {
                action = ACTION_BIND_PLAYER
            }
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return { runCatching { context.unbindService(connection) } }
        }

        fun getPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivityOpener::class.java),
            FLAGS,
        )

        fun getControlPendingIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getService(
                context, 0,
                Intent(context, PlayerService::class.java).apply { this.action = action },
                FLAGS,
            )

        fun getButtonReceiverPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setClass(context, PlayerCallback.ButtonReceiver::class.java)
                },
                FLAGS,
            )
    }

    class MainActivityOpener : android.app.Activity() {
        override fun onStart() {
            super.onStart()
            finish()
            startActivity(Intent(this, getMainActivity()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("fromNotification", true)
            })
        }
    }
}
