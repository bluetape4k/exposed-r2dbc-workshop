plugins {
    alias(libs.plugins.exposed)
    application
    alias(libs.plugins.kotlin.serialization)
}

exposed {
    migrations {
        tablesPackage = "exposed.examples.ktor.exposedintegration"
        databaseUrl = "jdbc:h2:mem:13-ecosystem-integrations-05-ktor-exposed-integration-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

application {
    mainClass.set("exposed.examples.ktor.exposedintegration.KtorExposedIntegrationApplicationKt")
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.bluetape4k.r2dbc)

    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.exposed.r2dbc)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.reactor)
}
