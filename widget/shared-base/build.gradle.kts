plugins {
    id("iris.widget")
}

android {
    namespace = "com.iris.widget"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
}
