plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.spring.modulith.publications.SpringModulithPublicationApplicationKt")
}

dependencies {
    implementation(platform(libs.spring.modulith.bom))

    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.bluetape4k.r2dbc)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.modulith.starter.core)

    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.spring.modulith.core)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation(libs.kotlinx.coroutines.test)
}
