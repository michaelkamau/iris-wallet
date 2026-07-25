plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.data.model.testing"
}

dependencies {
    implementation(projects.shared.data.model)

    implementation(libs.bundles.testing)
}