package dev.brahmkshatriya.echo.playback.source

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.ResolvingDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.source.CompositeMediaSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.Allocator
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.playback.MediaItemUtils
import dev.brahmkshatriya.echo.playback.MediaItemUtils.backgroundIndex
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaItemUtils.retries
import dev.brahmkshatriya.echo.playback.MediaItemUtils.serverIndex
import dev.brahmkshatriya.echo.playback.MediaItemUtils.sourceIndex
import dev.brahmkshatriya.echo.playback.MediaItemUtils.subtitleIndex
import dev.brahmkshatriya.echo.playback.PlayerService.Companion.select
import dev.brahmkshatriya.echo.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class StreamableMediaSource(
    private var mediaItem: MediaItem,
    private val app: App,
    private val scope: CoroutineScope,
    private val state: PlayerState,
    private val loader: StreamableLoader,
    private val cacheFactories: Factories,
    private val factories: Factories,
    private val changeFlow: MutableSharedFlow<Pair<MediaItem, MediaItem>>,
) : CompositeMediaSource<Nothing>() {

    private var error: Throwable? = null
    override fun maybeThrowSourceInfoRefreshError() {
        error?.let { throw IOException(it) }
        super.maybeThrowSourceInfoRefreshError()
    }

    fun getFactory(source: Streamable.Source) = if (source.isLive) factories else cacheFactories

    private lateinit var actualSource: MediaSource
    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val handler = Util.createHandlerForCurrentLooper()
        scope.launch {
            var (new, serv) = runCatching { loader.load(mediaItem) }.getOrElse {
                error = it
                return@launch
            }
            val server = serv.getOrNull()
            state.servers[new.mediaId] = serv
            state.serverChanged.emit(Unit)
            val sources = server?.sources
            actualSource = when (sources?.size) {
                0, null -> factories.create(new, -1, null)
                1 -> {
                    val source = sources.first()
                    getFactory(source).create(new, 0, source)
                }

                else -> {
                    if (server.merged) MergingMediaSource(
                        *sources.mapIndexed { index, source ->
                            getFactory(source).create(new, index, source)
                        }.toTypedArray()
                    ) else {
                        val index = mediaItem.sourceIndex
                        val source = sources.getOrNull(index)
                            ?: sources.select(app, new.extensionId) { it.quality }
                        val newIndex = sources.indexOf(source)
                        new = MediaItemUtils.buildSource(new, newIndex)
                        getFactory(source).create(new, newIndex, source)
                    }
                }
            }

            changeFlow.emit(mediaItem to new)
            mediaItem = new

            handler.post {
                runCatching {
                    prepareChildSource(null, actualSource)
                }.getOrElse {
                    it.printStackTrace()
                }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(
        childSourceId: Nothing?, mediaSource: MediaSource, newTimeline: Timeline,
    ) = refreshSourceInfo(newTimeline)

    override fun getMediaItem() = mediaItem

    override fun createPeriod(
        id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long,
    ) = actualSource.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) =
        actualSource.releasePeriod(mediaPeriod)

    override fun canUpdateMediaItem(mediaItem: MediaItem) = run {
        this.mediaItem.apply {
            if (retries != mediaItem.retries) return@run false
            if (serverIndex != mediaItem.serverIndex) return@run false
            if (this.sourceIndex != mediaItem.sourceIndex) return@run false
            if (backgroundIndex != mediaItem.backgroundIndex) return@run false
            if (subtitleIndex != mediaItem.subtitleIndex) return@run false
        }
        if (::actualSource.isInitialized) actualSource.canUpdateMediaItem(mediaItem)
        else false
    }

    override fun updateMediaItem(mediaItem: MediaItem) {
        this.mediaItem = mediaItem
        actualSource.updateMediaItem(mediaItem)
    }

    data class Factories(
        val dash: Lazy<MediaSource.Factory>,
        val hls: Lazy<MediaSource.Factory>,
        val default: Lazy<MediaSource.Factory>,
    ) {
        fun create(mediaItem: MediaItem, index: Int, source: Streamable.Source?): MediaSource {
            val type = (source as? Streamable.Source.Http)?.type
            val factory = when (type) {
                Streamable.SourceType.DASH -> dash
                Streamable.SourceType.HLS -> hls
                Streamable.SourceType.Progressive, null -> default
            }
            val new = MediaItemUtils.buildForSource(mediaItem, index, source)
            return factory.value.createMediaSource(new)
        }
    }

    class Factory(
        private val app: App,
        private val scope: CoroutineScope,
        private val state: PlayerState,
        extensions: ExtensionLoader,
        cache: SimpleCache,
        downloadFlow: StateFlow<List<Downloader.Info>>,
        private val changeFlow: MutableSharedFlow<Pair<MediaItem, MediaItem>>,
    ) : MediaSource.Factory {

        private val loader = StreamableLoader(app, extensions.music, downloadFlow)

        val dataSourceFactory = StreamableDataSource.Factory(app.context)
        val streamableResolver = StreamableResolver(app.context, state.servers)

        private val cacheDataSource = ResolvingDataSource.Factory(
            CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory(dataSourceFactory),
            streamableResolver
        )

        private val dataSource = ResolvingDataSource.Factory(
            dataSourceFactory, streamableResolver
        )

        private val cacheFactories = createFactories(cacheDataSource)

        private val factories = createFactories(dataSource)

        private fun createFactories(dataSource: ResolvingDataSource.Factory) = Factories(
            lazily { DashMediaSource.Factory(dataSource) },
            lazily { HlsMediaSource.Factory(dataSource) },
            lazily { DefaultMediaSourceFactory(dataSource) }
        )

        private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
        private fun lazily(factory: () -> MediaSource.Factory) = lazy {
            factory().apply {
                drmSessionManagerProvider?.let { setDrmSessionManagerProvider(it) }
                loadErrorHandlingPolicy?.let { setLoadErrorHandlingPolicy(it) }
            }
        }

        override fun getSupportedTypes() = intArrayOf(
            C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_DASH
        )

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider,
        ): MediaSource.Factory {
            this.drmSessionManagerProvider = drmSessionManagerProvider
            return this
        }

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory {
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
            return this
        }

        override fun createMediaSource(mediaItem: MediaItem) = StreamableMediaSource(
            mediaItem, app, scope, state, loader, cacheFactories, factories, changeFlow
        )
    }
}
