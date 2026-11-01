package dev.brahmkshatriya.echo.playback.source

import android.content.Context
import androidx.core.net.toUri
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.playback.source.StreamableResolver.Companion.copy

class StreamableDataSource(
    private val defaultDataSourceFactory: Lazy<DefaultDataSource.Factory>,
    private val defaultHttpDataSourceFactory: Lazy<DefaultHttpDataSource.Factory>,
    private val rawDataSourceFactory: Lazy<RawDataSource.Factory>,
) : DataSource {

    class Factory(
        context: Context,
    ) : DataSource.Factory {
        private val defaultDataSourceFactory = lazy {
            DefaultDataSource.Factory(context, defaultHttpDataSourceFactory.value)
        }
        private val defaultHttpDataSourceFactory = lazy {
            DefaultHttpDataSource.Factory()
        }
        private val rawDataSourceFactory = lazy { RawDataSource.Factory() }
        override fun createDataSource() = StreamableDataSource(
            defaultDataSourceFactory, defaultHttpDataSourceFactory, rawDataSourceFactory
        )
    }

    private var source: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in listeners) {
            listeners.add(transferListener)
            source?.addTransferListener(transferListener)
        }
    }

    override fun getResponseHeaders(): Map<String, List<String>> =
        source?.responseHeaders ?: emptyMap()

    override fun getUri() = source?.uri

    override fun read(buffer: ByteArray, offset: Int, length: Int) =
        source?.read(buffer, offset, length) ?: throw Exception("Source not opened")

    override fun close() {
        try {
            source?.close()
        } finally {
            source = null
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val result = dataSpec.customData as? Result<*>
        val (factory, spec) = when (result) {
            null -> defaultDataSourceFactory to dataSpec
            else -> when (val streamable = result.getOrThrow() as Streamable.Source) {
                is Streamable.Source.Raw -> rawDataSourceFactory to
                        dataSpec.copy(uri = streamable.uri, customData = streamable)

                is Streamable.Source.Http -> {
                    val spec = streamable.request.run {
                        dataSpec.copy(uri = url.toUri(), httpRequestHeaders = headers)
                    }
                    defaultDataSourceFactory to spec
                }
            }
        }
        val source = factory.value.createDataSource()
        listeners.forEach(source::addTransferListener)
        this.source = source
        try {
            return source.open(spec)
        } catch (failure: Throwable) {
            try { close() } catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }

    companion object {
        val Streamable.Source.uri
            get() = when (this) {
                is Streamable.Source.Http -> request.url.toUri()
                is Streamable.Source.Raw -> "raw://${id.hashCode()}".toUri()
            }
    }
}
