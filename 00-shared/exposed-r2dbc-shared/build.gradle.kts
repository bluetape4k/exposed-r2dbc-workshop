plugins {
    alias(libs.plugins.kotlin.serialization)
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {

    // Exposed R2DBC 의존성
    implementation(libs.jetbrains.exposed.core)
    implementation(libs.jetbrains.exposed.dao)
    implementation(libs.jetbrains.exposed.jdbc)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.jetbrains.exposed.crypt)
    implementation(libs.jetbrains.exposed.json)
    implementation(libs.jetbrains.exposed.money)
    implementation(libs.jetbrains.exposed.migration.r2dbc)

    // bluetape4k 테스트/검증 의존성
    implementation(libs.exposed.r2dbc)
    implementation(libs.bluetape4k.jdbc)
    implementation(libs.bluetape4k.r2dbc)
    implementation(libs.r2dbc.spi)
    implementation(libs.r2dbc.postgresql)
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

    // 코루틴 지원 의존성
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)

    // Kotlin Serialization JSON 지원
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)

    // Java Money 금액 타입 지원
    implementation(libs.bluetape4k.money)
    implementation(libs.javax.money.api)
    implementation(libs.javamoney.moneta)

    // 로그 캡처 테스트 지원
    api(libs.logcaptor)
}
