package dev.brahmkshatriya.echo.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.brahmkshatriya.echo.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

object ContextUtils {
    fun appVersion() = BuildConfig.VERSION_NAME + " " + BuildConfig.BUILD_TYPE
    fun getArch(): String {
        if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS.firstOrNull()?.let { return it }
        }
        @Suppress("DEPRECATION")
        Build.CPU_ABI.takeIf { it.isNotBlank() }?.let { return it }
        return System.getProperty("os.arch")
            ?: System.getProperty("os.product.cpu.abi")
            ?: "Unknown"
    }

    fun Context.copyToClipboard(label: String?, string: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, string)
        clipboard.setPrimaryClip(clip)
    }

    fun <T> LifecycleOwner.observe(flow: Flow<T>, block: suspend (T) -> Unit) =
        lifecycleScope.launch {
            flow.flowWithLifecycle(lifecycle).collectLatest(block)
        }

    fun <T> LifecycleOwner.collect(flow: Flow<T>, block: suspend (T) -> Unit) =
        lifecycleScope.launch {
            flow.collect {
                runCatching { block(it) }
            }
        }

    fun <T> LifecycleOwner.emit(flow: MutableSharedFlow<T>, value: T) {
        lifecycleScope.launch {
            flow.emit(value)
        }
    }

    const val SETTINGS_NAME = "settings"
    fun Context.getSettings() = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)!!

    private fun Context.getTempDir() = cacheDir.resolve("apks").apply { mkdirs() }
    fun Context.getTempFile(ext: String = "apk"): File =
        File.createTempFile("temp", ".$ext", getTempDir())

    fun Context.cleanupTempApks() {
        getTempDir().deleteRecursively()
    }
}
