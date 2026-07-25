plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.disclaimer"
}

dependencies {
    implementation(projects.shared.data.core)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    testImplementation(projects.shared.ui.testing)
}
