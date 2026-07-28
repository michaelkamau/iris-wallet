plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.navigation"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)

    testImplementation(projects.shared.ui.testing)
    testImplementation(libs.cashapp.molecule.runtime)
}
