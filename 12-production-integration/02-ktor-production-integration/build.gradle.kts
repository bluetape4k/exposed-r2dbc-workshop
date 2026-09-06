plugins {
    alias(libs.plugins.exposed)
    alias(libs.plugins.kotlin.serialization)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.production"
        databaseUrl = "jdbc:h2:mem:12-production-integration-02-ktor-production-integration-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))

    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)
    implementation(libs.bluetape4k.http.snapshot)

    // HTTP provider의 published timestamp train 전체를 immutable 버전으로 고정한다.
    constraints {
        implementation(libs.bluetape4k.core.snapshot)
        implementation(libs.bluetape4k.coroutines.snapshot)
        implementation(libs.bluetape4k.io.snapshot)
        implementation(libs.bluetape4k.logging.snapshot)
        implementation(libs.bluetape4k.netty.snapshot)
        implementation(libs.bluetape4k.resilience4j.snapshot)
        implementation(libs.bluetape4k.virtualthread.api.snapshot)
        implementation(libs.bluetape4k.virtualthread.jdk25.snapshot)
    }

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation("org.springframework:spring-core")
    implementation("org.springframework.security:spring-security-crypto")

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.spi)
    runtimeOnly(libs.logback)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.logcaptor)
    testImplementation(libs.bluetape4k.ktor.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}
