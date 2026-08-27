plugins {
    alias(libs.plugins.exposed)
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.exposed.batch)
    implementation(libs.jetbrains.exposed.core)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.exposed.r2dbc)
    implementation(libs.bluetape4k.jackson3)

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.pool)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
