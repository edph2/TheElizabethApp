pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PiperVoiceEngine"

// voiceformat (pure Kotlin) always builds. The Android app is only included when an Android
// SDK is present, so the file-format tests run on any machine with a JDK.
include(":voiceformat")
if (androidSdkAvailable()) include(":engine")

fun androidSdkAvailable(): Boolean {
    if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) return true
    val local = file("local.properties")
    return local.exists() && local.readText().contains("sdk.dir")
}
