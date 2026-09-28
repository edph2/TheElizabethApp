import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The voice-file format shared by the app and the speech engine: encrypted files (ELIZBAK2),
// voice package manifests, consent records. Apache-2.0 (see LICENSE in this folder), so it can
// be used by both the proprietary app and the GPL speech engine.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test-junit"))
}
