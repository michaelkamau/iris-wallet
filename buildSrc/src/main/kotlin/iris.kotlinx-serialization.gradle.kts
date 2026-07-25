plugins {
    id("iris.kotlin-android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(catalog.library("kotlinx-serialization-json"))
}
