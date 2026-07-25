plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.ui"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
}