plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlinx.serialization)
}

val gitHash = execute("git", "rev-parse", "HEAD").take(7)
val gitCount = execute("git", "rev-list", "--count", "HEAD").toInt()
val version = "3.0.$gitCount"

android {
    namespace = "dev.brahmkshatriya.echo"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo"
        minSdk = 18
        multiDexEnabled = true
        targetSdk = 36
        versionCode = gitCount
        versionName = "v${version}_$gitHash($gitCount)"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            resValue("string", "app_name", "Echo Nightly")
        }
        create("stable") {
            initWith(getByName("release"))
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// Resolve the JVM distribution of the exact same OkHttp release. Its Android
// distribution rejects API <21 during Platform initialization. The JVM ABI is
// still okhttp3.*; Conscrypt is installed before any client is constructed.
// Keep transitive AndroidX upgrades inside the verified API18 matrix.
configurations.all {
    resolutionStrategy {
        force("androidx.startup:startup-runtime:1.1.1")
        dependencySubstitution {
            substitute(module("com.squareup.okhttp3:okhttp"))
                .using(module("com.squareup.okhttp3:okhttp-jvm:5.1.0"))
            substitute(module("com.squareup.okhttp3:okhttp-android"))
                .using(module("com.squareup.okhttp3:okhttp-jvm:5.1.0"))
        }
        eachDependency {
            val version = when (requested.group) {
                "androidx.core" -> if (requested.name in listOf("core", "core-ktx")) "1.9.0" else null
                "androidx.appcompat" -> "1.6.1"
                "androidx.activity" -> "1.8.2"
                "androidx.fragment" -> "1.5.7"
                "androidx.lifecycle" -> "2.6.1"
                "androidx.navigation" -> "2.5.3"
                "androidx.work" -> "2.8.1"
                "androidx.savedstate" -> "1.2.1"
                "com.squareup.okhttp3" -> "5.1.0"
                else -> null
            }
            if (version != null) useVersion(version)
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":common"))
    implementation(libs.kotlin.reflect)
    implementation(libs.conscrypt)
    implementation(libs.bundles.androidx)
    implementation("androidx.transition:transition:1.4.1")
    implementation(libs.bundles.room)
    kapt(libs.room.compiler)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.exoplayer)
    implementation(libs.bundles.glide)

    implementation(libs.filekache)
    implementation(libs.pikolo)
    implementation(libs.fadingedgelayout)
    implementation(libs.kenburnsview)
}

fun execute(vararg command: String): String = providers.exec {
    commandLine(*command)
}.standardOutput.asText.get().trim()
