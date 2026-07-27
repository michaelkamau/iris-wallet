plugins {
    id("iris.kotlin-android")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

// KSP automatically registers its generated-sources directory with the built-in
// Kotlin compilation, so no manual kotlin.sourceSets wiring is needed anymore.

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.androidx.work)
    ksp(catalog.library("hilt-compiler"))
    ksp(catalog.library("hilt-work-compiler"))
}
