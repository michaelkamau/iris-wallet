plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.design"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.ui.core)

    implementation(projects.shared.domain)
}