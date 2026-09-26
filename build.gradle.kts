// All plugins share one classpath here so the Kotlin plugin can see the Android
// plugin. The Android plugin is only added when an SDK is available (see
// settings.gradle.kts).
buildscript {
    val kotlinVersion = "2.1.0"
    val agpVersion = "8.7.3"
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        if (hasAndroidSdk) {
            classpath("com.android.tools.build:gradle:$agpVersion")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        }
    }
}
