# Extensions are compiled independently and loaded through the host classloader.
-keep class dev.brahmkshatriya.echo.common.** { *; }
-keep class echo.common.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.protobuf.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Conscrypt uses JNI and must remain available to OkHttp's provider detection.
-keep class org.conscrypt.** { *; }
# Optional JVM TLS providers are not included in the Android application.
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.net.ssl.ExtendedSSLSession
-dontwarn javax.net.ssl.SNIHostName
