plugins {
    alias(libs.plugins.kotlin.serialization)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(libs.exposed.bom))

    // Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.crypt)
    implementation(libs.exposed.json)
    implementation(libs.exposed.money)
    implementation(libs.exposed.migration.r2dbc)

    // bluetape4k
    implementation(libs.bluetape4k.exposed.r2dbc)
    implementation(libs.bluetape4k.jdbc)
    implementation(libs.bluetape4k.r2dbc)
    implementation(libs.bluetape4k.junit5)
    implementation(libs.bluetape4k.assertions)

    implementation(libs.h2.v2)

    testImplementation(libs.r2dbc.spi)
    testImplementation(libs.r2dbc.pool)
    testImplementation(libs.r2dbc.h2)
    testImplementation(libs.r2dbc.mariadb)
    testImplementation(libs.r2dbc.mysql)
    testImplementation(libs.r2dbc.postgresql)

    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.postgresql)

    // Testcontainers 를 위한 DB 드라이버
    implementation(libs.mariadb.java.client)
    implementation(libs.mysql.connector.j)
    implementation(libs.postgresql.driver)

    // Identifier 자동 생성
    implementation(libs.bluetape4k.idgenerators)
    implementation(libs.java.uuid.generator)

    // Coroutines
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)

    // Kotlin Serialization Json
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)

    // Java Money
    implementation(libs.bluetape4k.money)
    implementation(libs.javax.money.api)
    implementation(libs.javamoney.moneta)

    // Logcaptor
    api(libs.logcaptor)
}
