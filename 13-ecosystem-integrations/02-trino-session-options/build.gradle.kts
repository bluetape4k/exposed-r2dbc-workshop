configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.spi)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.h2)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
