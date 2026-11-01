package dev.brahmkshatriya.echo.playback.source

import android.net.Uri
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import dev.brahmkshatriya.echo.common.models.Streamable
import kotlinx.coroutines.runBlocking
import java.io.InputStream

class RawDataSource : BaseDataSource(true) {

    class Factory : DataSource.Factory {
        override fun createDataSource() = RawDataSource()
    }

    private var stream: InputStream? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val streamable = dataSpec.customData as Streamable.Source.Raw
        val (source, total) = runBlocking {
            streamable.streamProvider!!.provide(dataSpec.position, dataSpec.length)
        }
        uri = dataSpec.uri
        stream = source
        transferStarted(dataSpec)
        return total
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        return stream!!.read(buffer, offset, length).also {
            if (it > 0) bytesTransferred(it)
        }
    }

    override fun getUri() = uri

    override fun close() {
        val opened = stream != null
        try {
            stream?.close()
        } finally {
            stream = null
            uri = null
            if (opened) transferEnded()
        }
    }
}
