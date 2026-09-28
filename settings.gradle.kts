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

// The pure-Kotlin modules always build: voiceformat (Apache-2.0, shared) and core (the
// app's logic). The Android apps (the app, and the GPL speech engine) are only included
// when an Android SDK is present, so the core
// can be built and tested on any machine with a JDK.
include(":voiceformat", ":core")
if (androidSdkAvailable()) include(":app", ":engine")

fun androidSdkAvailable(): Boolean {
    if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) return true
    val local = file("local.properties")
    return local.exists() && local.readText().contains("sdk.dir")
}
