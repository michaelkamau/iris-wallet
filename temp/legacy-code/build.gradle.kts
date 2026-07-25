plugins {
    id("iris.feature")
    id("iris.room")
}

android {
    namespace = "com.iris.legacy"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.oldDesign)

    implementation(libs.bundles.activity)
    implementation(libs.bundles.opencsv)
    implementation(libs.bundles.ktor)
    implementation(libs.androidx.work)
    implementation(libs.datastore)
    implementation(libs.keval)
    implementation(libs.androidx.recyclerview)
}
