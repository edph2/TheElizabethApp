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
        classpath("org.cyclonedx:cyclonedx-gradle-plugin:1.10.0")
        if (hasAndroidSdk) {
            classpath("com.android.tools.build:gradle:$agpVersion")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        }
    }
}

// Software bill of materials (CycloneDX): every library in the app, with versions and
// licences, so what ships on her tablet can be checked. ./gradlew cyclonedxBom -> build/reports/
allprojects {
    group = "uk.elizabeth.aac"
    version = "0.1.0"
}
apply(plugin = "org.cyclonedx.bom")
tasks.named("cyclonedxBom") {
    // Only what ships in the app, not build tools or test libraries.
    withGroovyBuilder {
        "setIncludeConfigs"(listOf("runtimeClasspath", "releaseRuntimeClasspath"))
        "setSkipConfigs"(listOf(".*[Tt]est.*", "kotlinCompilerClasspath", "kotlinBuildToolsApiClasspath"))
    }
}
