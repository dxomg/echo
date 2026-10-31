package dev.brahmkshatriya.echo.playback.source

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.ResolvingDataSource.Resolver
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.playback.MediaItemUtils.toKey
import dev.brahmkshatriya.echo.playback.source.StreamableDataSource.Companion.uri
import dev.brahmkshatriya.echo.utils.CacheUtils.saveToCache
import java.util.WeakHashMap

class StreamableResolver(
    private val context: Context,
    private val current: WeakHashMap<String, Result<Streamable.Media.Server>>,
) : Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val (id, index) = dataSpec.uri.toString().toKey().getOrNull() ?: return dataSpec
        val streamable = runCatching { current[id]!!.getOrThrow().sources[index] }
        val uri = streamable.map {
            if (!it.isLive)
                context.saveToCache(it.uri.toString(), dataSpec.uri.toString(), "player")
            it.uri
        }
        return dataSpec.copy(uri = uri.getOrNull(), customData = streamable)
    }

    companion object {

        fun DataSpec.copy(
            uri: Uri? = null,
            uriPositionOffset: Long? = null,
            httpMethod: Int? = null,
            httpBody: ByteArray? = null,
            httpRequestHeaders: Map<String, String>? = null,
            position: Long? = null,
            length: Long? = null,
            key: String? = null,
            flags: Int? = null,
            customData: Any? = null,
        ): DataSpec {
            return DataSpec.Builder()
                .setUri(uri ?: this.uri)
                .setUriPositionOffset(uriPositionOffset ?: this.uriPositionOffset)
                .setHttpMethod(httpMethod ?: this.httpMethod)
                .setHttpBody(httpBody ?: this.httpBody)
                .setHttpRequestHeaders(httpRequestHeaders ?: this.httpRequestHeaders)
                .setPosition(position ?: this.position)
                .setLength(length ?: this.length)
                .setKey(key ?: this.key)
                .setFlags(flags ?: this.flags)
                .setCustomData(customData ?: this.customData)
                .build()
        }
    }
}
