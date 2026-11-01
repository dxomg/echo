package dev.brahmkshatriya.echo

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import dev.brahmkshatriya.echo.di.DI
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.utils.AppShortcuts.configureAppShortcuts
import dev.brahmkshatriya.echo.utils.CoroutineUtils
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.conscrypt.Conscrypt
import java.security.Security

class MainApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        // Must precede Koin, extension loading and the first OkHttp Platform
        // access. This also gives extension-created OkHttpClient instances TLS
        // 1.2/1.3 without changing their shared okhttp3/common ABI.
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        startKoin {
            androidContext(this@MainApplication)
            workManagerFactory()
            modules(DI.appModule)
        }
        CoroutineUtils.setDebug()
        applyLocale(settings)
        configureAppShortcuts(extensionLoader)
    }

    private val settings by inject<SharedPreferences>()
    private val extensionLoader by inject<ExtensionLoader>()

    override fun getPackageName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching {
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.equals(CLASS_NAME, ignoreCase = true)
                        && FUNCTION_SET.any { trace.methodName.equals(it, ignoreCase = true) }
            }
            if (isChromiumCall) return spoofedPackageName(applicationContext)
        }
        return super.getPackageName()
    }

    private fun spoofedPackageName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(CHROME_PACKAGE, PackageManager.GET_META_DATA)
            CHROME_PACKAGE
        }.getOrElse {
            SYSTEM_SETTINGS_PACKAGE
        }
    }

    companion object {
        private const val CHROME_PACKAGE = "com.android.chrome"
        private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"
        private const val CLASS_NAME = "org.chromium.base.BuildInfo"
        private val FUNCTION_SET = setOf("getAll", "getPackageName", "<init>")

        fun getCurrentLanguage(sharedPref: SharedPreferences) =
            sharedPref.getString("language", null) ?: "system"

        fun setCurrentLanguage(sharedPref: SharedPreferences, locale: String?) {
            sharedPref.edit { putString("language", locale) }
            applyLocale(sharedPref)
        }

        fun applyLocale(sharedPref: SharedPreferences) {
            val value = sharedPref.getString("language", null) ?: "system"
            val locale = if (value == "system") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(value)
            AppCompatDelegate.setApplicationLocales(locale)
        }

        val languages = mapOf(
            "ar" to "العربية",
            "as" to "Assamese",
            "be" to "Беларуская",
            "bn" to "বাংলা",
            "ca" to "Català",
            "de" to "Deutsch",
            "es" to "Español",
            "fa" to "فارسی",
            "fr" to "Français",
            "en" to "English",
            "hi" to "हिन्दी",
            "hng" to "Hinglish",
            "hu" to "Magyar",
            "in" to "Bahasa Indonesia",
            "it" to "Italiano",
            "iw" to "עברית",
            "ja" to "日本語",
            "ko" to "한국어",
            "lv" to "Latviski",
            "ms" to "Bahasa Melayu",
            "pl" to "Polski",
            "pt" to "Português",
            "pt-rBR" to "Português (Brasil)",
            "ru" to "Русский",
            "sa" to "संस्कृतम्",
            "si" to "සිංහල",
            "sk" to "Slovenčina",
            "sr" to "Српски",
            "ta" to "தமிழ்",
            "th" to "ไทย",
            "tl" to "Filipino",
            "tr" to "Türkçe",
            "uk" to "Українська",
            "vi" to "Tiếng Việt",
            "zh-rCN" to "中文 (简体)",
            "zh-rTW" to "中文 (繁體)"
        )
    }
}
