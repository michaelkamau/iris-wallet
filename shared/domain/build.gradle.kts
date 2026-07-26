plugins {
    id("iris.feature")
    id("iris.integration.testing")
    id("iris.room")
}

android {
    namespace = "com.iris.domain"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.sms.parser)

    implementation(libs.datastore)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.opencsv)

    testImplementation(projects.shared.data.modelTesting)
    testImplementation(projects.shared.data.coreTesting)

    androidTestImplementation(libs.bundles.integration.testing)
    androidTestImplementation(libs.mockk.android)
}