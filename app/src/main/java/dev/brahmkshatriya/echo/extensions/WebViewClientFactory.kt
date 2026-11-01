package dev.brahmkshatriya.echo.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.brahmkshatriya.echo.MainActivity.Companion.getMainActivity
import dev.brahmkshatriya.echo.common.helpers.WebViewClient
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Metadata
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class WebViewClientFactory(
    private val context: Context,
) {

    val requests = ConcurrentHashMap<Int, Wrapper>()
    private val nextId = AtomicInteger()
    val responseFlow = MutableSharedFlow<Pair<Wrapper, Result<String?>?>>()

    suspend fun await(
        ext: Metadata, showWebView: Boolean, reason: String, request: WebViewRequest<String>,
    ): Result<String?> = coroutineScope {
        val wrapper = Wrapper(ext, showWebView, reason, request)
        val id = nextId.incrementAndGet()
        requests[id] = wrapper
        // Subscribe before launching the activity so a fast completion cannot
        // be lost by the non-replaying response flow.
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            responseFlow.first { it.first === wrapper && it.second != null }.second!!
        }
        try {
            startWebView(id)
            response.await()
        } finally {
            response.cancel()
            requests.remove(id)
        }
    }

    private fun startWebView(id: Int) {
        PendingIntent.getActivity(
            context,
            id,
            Intent(context, context.getMainActivity()).apply {
                putExtra("webViewRequest", id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0),
        ).send()
    }

    fun createFor(metadata: Metadata) = object : WebViewClient {
        override suspend fun await(
            showWebView: Boolean,
            reason: String,
            request: WebViewRequest<String>,
        ): Result<String?> = await(metadata, showWebView, reason, request)
    }

    data class Wrapper(
        val extension: Metadata,
        val showWebView: Boolean,
        val reason: String,
        val request: WebViewRequest<String>,
    )

}
