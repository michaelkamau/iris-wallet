plugins {
    id("iris.feature")
    id("iris.room")
}

android {
    namespace = "com.iris.data.testing"
}

dependencies {
    implementation(projects.shared.data.core)
}
