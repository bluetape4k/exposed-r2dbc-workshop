plugins {
    kotlin("plugin.serialization")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {

    testImplementation(project(":exposed-r2dbc-shared"))

    // Exposed
    testImplementation(libs.jetbrains.exposed.r2dbc)
    testImplementation(libs.jetbrains.exposed.json)
    testImplementation(libs.jetbrains.exposed.migration.r2dbc)

    // java time 지원 라이브러리
    testImplementation(libs.jetbrains.exposed.kotlin.datetime)

    // Kotlin Serialization JSON
    testImplementation(platform(libs.kotlinx.serialization.bom))
    testImplementation(libs.kotlinx.serialization.json)

    testImplementation(libs.exposed.r2dbc)
    testImplementation(libs.bluetape4k.junit5)

    testRuntimeOnly(libs.h2.v2)

    testRuntimeOnly(libs.r2dbc.spi)
    testRuntimeOnly(libs.r2dbc.pool)
    testRuntimeOnly(libs.r2dbc.h2)
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.postgresql)

    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.postgresql)

    // Testcontainers 를 위한 DB 드라이버
    testRuntimeOnly(libs.mariadb.java.client)
    testRuntimeOnly(libs.mysql.connector.j)
    testRuntimeOnly(libs.postgresql.driver)

    // 코루틴 지원 의존성
    testImplementation(libs.bluetape4k.coroutines)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)
}
