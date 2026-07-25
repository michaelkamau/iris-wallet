plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.ui.testing"
}

dependencies {
    implementation(projects.shared.ui.core)

    // for this module we need test deps as "implementation" and not only "testImplementation"
    // because it'll be added as "testImplementation"
    implementation(libs.bundles.testing)
    implementation(libs.paparazzi)
    // Used directly (not via the app.cash.molecule Gradle plugin) since that plugin hardcodes
    // an incompatible, no-longer-updated Compose compiler artifact
    // (org.jetbrains.compose.compiler:compiler) that clashes with the official
    // org.jetbrains.kotlin.plugin.compose plugin already applied via iris.compose.
    implementation(libs.cashapp.molecule.runtime)
}
