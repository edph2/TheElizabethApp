import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.elizabeth.aac"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.elizabeth.aac"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Real tablets only: keeps the on-device speech engine (native code) out of the APK for other CPUs.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
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

// The on-device speech engine for her own voice: sherpa-onnx (Apache-2.0) with ONNX Runtime (MIT).
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
    implementation(project(":core"))
    implementation(files(sherpaOnnxAar))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
