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

rootProject.name = "TheElizabethApp"

// The pure-Kotlin core (touch filtering, prediction, privacy log) always builds.
// The Android app is only included when an Android SDK is present, so the core
// can be built and tested on any machine with a JDK.
include(":core")
if (androidSdkAvailable()) include(":app")

fun androidSdkAvailable(): Boolean {
    if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) return true
    val local = file("local.properties")
    return local.exists() && local.readText().contains("sdk.dir")
}
