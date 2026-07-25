plugins {
    id("iris.script")
    application
}

application {
    mainClass = "iris.automate.pr.MainKt"
}

dependencies {
    implementation(projects.ciActions.base)
}
