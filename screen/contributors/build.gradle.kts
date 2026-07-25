plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.contributors"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    implementation(libs.bundles.ktor)

    testImplementation(projects.shared.ui.testing)
}
