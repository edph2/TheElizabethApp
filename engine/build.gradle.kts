import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

// The Piper Voice Engine: a standard Android text-to-speech engine for Piper voices.
// Licensed GPL-3.0-or-later (see LICENSE in this folder), because it includes espeak-ng through
// sherpa-onnx. It is a separate app: the proprietary communication app talks to it only through
// Android's TextToSpeech API and the documented voice-management provider.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.elizabeth.speech"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.elizabeth.speech"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Real tablets only: keeps the on-device speech engine (native code) out of the APK for other CPUs.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing. The key is never in the repository: it is supplied by environment
    // variables (from secrets when built in CI). Apps that manage the engine's voices must be
    // signed with the same key.
    val keystore = System.getenv("ELIZABETH_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ELIZABETH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ELIZABETH_KEY_ALIAS")
                keyPassword = System.getenv("ELIZABETH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Also x86_64, so instrumented tests (including real speech synthesis) run on a PC emulator.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    // No Google dependency-metadata block in the APK: nothing about the build is sent anywhere.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// The on-device speech engine: sherpa-onnx (Apache-2.0) with ONNX Runtime (MIT) and espeak-ng (GPL-3.0).
// It is not on Maven Central, so the release AAR is downloaded once from GitHub and its
// SHA-256 checked, so the build always uses exactly this reviewed file.
val sherpaOnnxVersion = "1.13.8"
val sherpaOnnxSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaOnnxAar = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")

fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

if (!sherpaOnnxAar.exists() || sha256(sherpaOnnxAar) != sherpaOnnxSha256) {
    sherpaOnnxAar.parentFile.mkdirs()
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
    logger.lifecycle("Downloading $url")
    URI(url).toURL().openStream().use { input -> sherpaOnnxAar.outputStream().use { input.copyTo(it) } }
    val actual = sha256(sherpaOnnxAar)
    if (actual != sherpaOnnxSha256) {
        sherpaOnnxAar.delete()
        throw GradleException("sherpa-onnx AAR has SHA-256 $actual, expected $sherpaOnnxSha256")
    }
}

dependencies {
    implementation(project(":voiceformat"))
    implementation(files(sherpaOnnxAar))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Same lifecycle versions as the app, so both resolve the same verified artifacts.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Same version the app's tests resolve, so dependency verification covers it.
    androidTestImplementation("androidx.collection:collection:1.4.4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
