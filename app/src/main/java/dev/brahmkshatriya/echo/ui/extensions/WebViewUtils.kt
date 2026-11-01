package dev.brahmkshatriya.echo.ui.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import java.util.Collections
import dev.brahmkshatriya.echo.utils.ui.legacy.EchoProgressBar
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.databinding.FragmentGenericCollapsableBinding
import dev.brahmkshatriya.echo.databinding.FragmentWebviewBinding
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.ui.common.FragmentUtils.addIfNull
import dev.brahmkshatriya.echo.ui.common.FragmentUtils.openFragment
import dev.brahmkshatriya.echo.ui.common.SnackBarHandler.Companion.createSnack
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.applyBackPressCallback
import dev.brahmkshatriya.echo.ui.extensions.login.LoginFragment.Companion.bind
import dev.brahmkshatriya.echo.utils.image.ImageUtils.loadAsCircle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebViewUtils {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 2; Jeff Bezos) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.158 Mobile Safari/537.36"

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    fun <T> FragmentActivity.configure(
        webView: WebView,
        progress: EchoProgressBar,
        target: WebViewRequest<T>,
        skipTimeout: Boolean,
        onComplete: suspend (Result<T>?) -> Unit,
    ): OnBackPressedCallback {
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                webView.goBack()
            }
        }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().run {
            if (Build.VERSION.SDK_INT >= 21) {
                removeAllCookies(null)
                flush()
            } else {
                android.webkit.CookieSyncManager.createInstance(this@configure)
                removeAllCookie()
                android.webkit.CookieSyncManager.getInstance().sync()
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = USER_AGENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                isAlgorithmicDarkeningAllowed = true
        }
        runCatching {
            webView.load(lifecycleScope, progress, callback, target, skipTimeout, onComplete)
        }.getOrElse {
            lifecycleScope.launch {
                webView.stop(callback)
                onComplete(Result.failure(it))
            }
        }
        return callback
    }

    private fun <T> WebView.load(
        scope: CoroutineScope,
        progress: EchoProgressBar,
        callback: OnBackPressedCallback,
        target: WebViewRequest<T>,
        skipTimeout: Boolean,
        onComplete: suspend (Result<T>?) -> Unit,
    ) {
        val stopRegex = target.stopUrlRegex
        val interceptRegex =
            if (target is WebViewRequest.Headers) target.interceptUrlRegex else null
        val timeout = target.maxTimeout
        val bridge = Bridge()
        val requests = Collections.synchronizedList(mutableListOf<NetworkRequest>())
        val timeoutJob = if (!skipTimeout) scope.launch {
            delay(timeout)
            onComplete(
                Result.failure(
                    Exception(
                        "WebView request timed out after $timeout ms\nParsed Links:\n" +
                                requests.joinToString("\n") { it.url }
                    )
                )
            )
        } else null
        webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                callback.isEnabled = canGoBack()
            }

            var done = false
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view.executeJS(REQUEST_INSPECTOR)
                progress.show()
                if (done) return
                if (target !is WebViewRequest.Evaluate) return
                target.javascriptToEvaluateOnPageStart?.let { js ->
                    scope.launch {
                        runCatching { evalJS(null, js) }.onFailure {
                            stop(callback)
                            onComplete(Result.failure(it))
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.executeJS(REQUEST_INSPECTOR)
                progress.hide()
            }

            val mutex = Mutex()
            fun intercept(request: NetworkRequest) {
                if (target is WebViewRequest.Headers) {
                    if (interceptRegex == null || interceptRegex.matches(request.url)) {
                        requests.add(request)
                    }
                }
                if (stopRegex.find(request.url) == null) return
                timeoutJob?.cancel()
                scope.launch(Dispatchers.IO) {
                    mutex.withLock {
                        if (done) return@withLock
                        done = true
                        onComplete(null)
                        val result = runCatching {
                            val headerRes = if (target is WebViewRequest.Headers)
                                target.onStop(synchronized(requests) { requests.toList() })
                            else null
                            val cookieRes = if (target is WebViewRequest.Cookie) {
                                val cookie =
                                    CookieManager.getInstance().getCookie(request.url) ?: ""
                                target.onStop(request, cookie)
                            } else null
                            val evalRes = if (target is WebViewRequest.Evaluate)
                                target.onStop(
                                    request,
                                    evalJS(bridge, target.javascriptToEvaluate)
                                )
                            else null
                            evalRes ?: cookieRes ?: headerRes!!
                        }
                        stop(callback)
                        onComplete(result)
                    }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?, url: String?,
            ): WebResourceResponse? {
                if (url != null) intercept(withCookies(NetworkRequest(url)))
                return null
            }

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?,
            ): WebResourceResponse? {
                request?.let {
                    intercept(withCookies(NetworkRequest(
                        runCatching { NetworkRequest.Method.valueOf(it.method.uppercase()) }
                            .getOrDefault(NetworkRequest.Method.GET),
                        it.url.toString(), it.requestHeaders ?: emptyMap()
                    )))
                }
                return null
            }

            fun withCookies(request: NetworkRequest): NetworkRequest {
                val headers = request.headers.toMutableMap()
                CookieManager.getInstance().getCookie(request.url)?.let { headers["Cookie"] = it }
                return request.copy(headers = headers)
            }

            init {
                addJavascriptInterface(RequestBridge { request -> intercept(withCookies(request)) }, "echoRequests")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) intercept(withCookies(NetworkRequest(url)))
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?,
            ): Boolean {
                request?.run {
                    val headers = requestHeaders?.toMutableMap() ?: mutableMapOf()
                    val cookie = CookieManager.getInstance().getCookie(url.toString())
                    if (cookie != null) headers["Cookie"] = cookie
                    intercept(NetworkRequest(url.toString(), headers))
                }
                return false
            }
        }

        addJavascriptInterface(bridge, "bridge")
        settings.cacheMode =
            if (runCatching { target.dontCache }.getOrNull() == true) WebSettings.LOAD_NO_CACHE
            else WebSettings.LOAD_DEFAULT
        target.initialUrl.run {
            settings.userAgentString = lowerCaseHeaders["user-agent"] ?: settings.userAgentString
            loadUrl(url, headers)
        }
    }

    suspend fun WebView.evalJS(bridge: Bridge?, js: String) = withContext(Dispatchers.Main) {
        require(js.startsWith("async function") || js.startsWith("function")) {
            "Invalid JS function, must start with async or function"
        }
        // API18's system WebView has no async/await parser or Promise. Do not
        // silently rewrite extension code: fail unsupported syntax explicitly.
        if (Build.VERSION.SDK_INT < 19 && js.startsWith("async function")) {
            error("Async JavaScript requires a newer WebView; synchronous functions work on Android 4.3")
        }
        val newJs = """
            (function() {
                try {
                    var fun = $js;
                    var result = fun();
                    if (result && typeof result.then === 'function') {
                        result.then(function(value) { bridge.putJsResult(value); },
                            function(error) { bridge.putJsError(String(error)); });
                    } else { bridge.putJsResult(result == null ? null : String(result)); }
                } catch (error) { bridge.putJsError(String(error)); }
            })()
        """.trimIndent()
        if (bridge == null) {
            executeJS("(function() { ($js)(); })()")
            return@withContext null
        }
        suspendCancellableCoroutine<String?> { continuation ->
            bridge.onResult = { if (continuation.isActive) continuation.resume(it) }
            bridge.onError = { if (continuation.isActive) continuation.resumeWithException(it) }
            executeJS(newJs)
            continuation.invokeOnCancellation {
                post { executeJS("window.stop();") }
            }
        }
    }

    private fun WebView.executeJS(js: String) {
        if (Build.VERSION.SDK_INT >= 19) evaluateJavascript(js, null)
        else loadUrl("javascript:$js")
    }

    class RequestBridge(private val onRequest: (NetworkRequest) -> Unit) {
        @JavascriptInterface
        fun capture(json: String) {
            runCatching {
                val data = JSONObject(json)
                val headers = data.optJSONObject("headers") ?: JSONObject()
                val request = NetworkRequest(
                    runCatching { NetworkRequest.Method.valueOf(data.optString("method", "GET").uppercase()) }
                        .getOrDefault(NetworkRequest.Method.GET),
                    data.getString("url"),
                    headers.keys().asSequence().associateWith { headers.getString(it) },
                    data.optString("body", "").encodeToByteArray()
                )
                onRequest(request)
            }
        }
    }

    // ES5 syntax is intentional: the API18 WebView cannot parse arrows, const,
    // or async functions. Hooks preserve XHR headers and textual request bodies.
    private val REQUEST_INSPECTOR = """
        (function() {
            if (window.echoRequestInspector) return;
            window.echoRequestInspector = true;
            function report(method, url, headers, body) {
                try {
                    var link = document.createElement('a'); link.href = url;
                    echoRequests.capture(JSON.stringify({method: method, url: link.href,
                        headers: headers || {}, body: typeof body === 'string' ? body : ''}));
                } catch (ignored) {}
            }
            var open = XMLHttpRequest.prototype.open;
            var header = XMLHttpRequest.prototype.setRequestHeader;
            var send = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this.echoRequest = {method: method, url: url, headers: {}};
                return open.apply(this, arguments);
            };
            XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
                if (this.echoRequest) this.echoRequest.headers[key] = value;
                return header.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function(body) {
                var r = this.echoRequest;
                if (r) report(r.method, r.url, r.headers, body);
                return send.apply(this, arguments);
            };
            if (window.fetch) {
                var fetch = window.fetch;
                window.fetch = function(input, options) {
                    options = options || {};
                    var headers = {}, source = options.headers || input.headers || {};
                    if (source.forEach) source.forEach(function(value, key) { headers[key] = value; });
                    else for (var key in source) if (Object.prototype.hasOwnProperty.call(source, key)) headers[key] = source[key];
                    report(options.method || input.method || 'GET', input.url || String(input), headers, options.body);
                    return fetch.apply(this, arguments);
                };
            }
        })();
    """.trimIndent()

    suspend fun WebView.stop(
        callback: OnBackPressedCallback,
    ) = withContext(Dispatchers.Main) {
        loadUrl("about:blank")
        callback.isEnabled = false
    }

    @Suppress("unused")
    class Bridge {
        var onError: ((Throwable) -> Unit)? = null
        var onResult: ((String?) -> Unit)? = null

        @JavascriptInterface
        fun putJsResult(result: String?) {
            onResult?.invoke(result)
        }

        @JavascriptInterface
        fun putJsError(error: String?) {
            onError?.invoke(Exception(error ?: "Unknown JavaScript error"))
        }
    }

    fun FragmentActivity.onWebViewIntent(
        intent: Intent,
    ) {
        val id = intent.getIntExtra("webViewRequest", -1)
        if (id == -1) return
        val extensionLoader by inject<ExtensionLoader>()
        val webViewClient = extensionLoader.webViewClientFactory
        val wrapper = webViewClient.requests[id] ?: return
        createSnack(Message(getString(R.string.opening_webview_x, wrapper.reason)))
        if (wrapper.showWebView) openFragment<WithAppbar>(null, getBundle(id))
        else supportFragmentManager.commit {
            add<Hidden>(R.id.hiddenWebViewContainer, null, getBundle(id))
        }
    }

    private fun getBundle(id: Int) = Bundle().apply {
        putInt("webViewRequest", id)
    }

    class Hidden : Fragment(R.layout.fragment_webview) {
        private val vm by activityViewModel<ExtensionsViewModel>()
        private val webViewClient by lazy { vm.extensionLoader.webViewClientFactory }
        private val wrapper by lazy {
            val id = requireArguments().getInt("webViewRequest")
            webViewClient.requests[id]
        }
        private val shouldRemove by lazy {
            requireArguments().getBoolean("hidden", true)
        }

        private fun removeSelf() {
            if (shouldRemove) parentFragmentManager.commit(true) { remove(this@Hidden) }
            else parentFragment?.parentFragmentManager?.popBackStack()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val binding = FragmentWebviewBinding.bind(view)
            val wrapper = wrapper ?: run {
                removeSelf()
                return
            }
            val callback = requireActivity().configure(
                binding.webview,
                binding.progress,
                wrapper.request,
                false
            ) {
                webViewClient.responseFlow.emit(wrapper to it)
                if (it == null) runCatching { removeSelf() }
            }
            if (!shouldRemove) {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
                applyBackPressCallback()
            }
        }
    }

    class WithAppbar : Fragment(R.layout.fragment_generic_collapsable) {
        private val vm by activityViewModel<ExtensionsViewModel>()
        private val webViewClient by lazy { vm.extensionLoader.webViewClientFactory }
        private val wrapper by lazy {
            val id = requireArguments().getInt("webViewRequest")
            webViewClient.requests[id] ?: throw IllegalStateException("Invalid webview request")
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val binding = FragmentGenericCollapsableBinding.bind(view)
            binding.bind(this)
            binding.toolBar.title = wrapper.extension.name
            wrapper.extension.icon.loadAsCircle(
                binding.extensionIcon, R.drawable.ic_extension_32dp
            ) {
                binding.extensionIcon.setImageDrawable(it)
            }
            addIfNull<Hidden>(R.id.genericFragmentContainer, "webview", arguments?.apply {
                putBoolean("hidden", false)
            })
        }
    }
}
