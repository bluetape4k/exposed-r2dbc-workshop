plugins {
    alias(libs.plugins.exposed)
    alias(libs.plugins.kotlin.serialization)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.cache"
        databaseUrl = "jdbc:h2:mem:11-high-performance-05-cache-strategies-ktor-r2dbc-coroutines-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))

    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.redisson)
    implementation(libs.bluetape4k.testcontainers)

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}
