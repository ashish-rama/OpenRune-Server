plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.rsprot.api)
    implementation(projects.api.generated)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
}
