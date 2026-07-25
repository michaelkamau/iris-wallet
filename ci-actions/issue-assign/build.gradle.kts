plugins {
    id("iris.script")
    application
}

application {
    mainClass = "iris.automate.issue.MainKt"
}

dependencies {
    implementation(projects.ciActions.base)
}
