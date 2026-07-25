plugins {
    id("iris.feature")
    id("iris.room")
    id("iris.integration.testing")
}

android {
    namespace = "com.iris.data"
}

dependencies {
    implementation(projects.shared.base)
    api(projects.shared.data.model)

    implementation(libs.datastore)
    implementation(libs.bundles.ktor)

    testImplementation(projects.shared.data.modelTesting)
    androidTestImplementation(libs.bundles.integration.testing)
}
