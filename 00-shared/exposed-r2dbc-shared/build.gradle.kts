plugins {
    kotlin("plugin.serialization")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    // Exposed
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_dao)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.exposed_java_time)
    implementation(Libs.exposed_crypt)
    implementation(Libs.exposed_json)
    implementation(Libs.exposed_money)
    implementation(Libs.exposed_migration_r2dbc)

    // bluetape4k
    implementation(Libs.bluetape4k_exposed_r2dbc)
    implementation(Libs.bluetape4k_jdbc)
    implementation(Libs.bluetape4k_r2dbc)
    implementation(Libs.bluetape4k_junit5)

    implementation(Libs.h2_v2)

    testImplementation(Libs.r2dbc_spi)
    testImplementation(Libs.r2dbc_pool)
    testImplementation(Libs.r2dbc_h2)
    testImplementation(Libs.r2dbc_mariadb)
    testImplementation(Libs.r2dbc_mysql)
    testImplementation(Libs.r2dbc_postgresql)

    implementation(Libs.bluetape4k_testcontainers)
    implementation(Libs.testcontainers)
    implementation(Libs.testcontainers_junit_jupiter)
    implementation(Libs.testcontainers_mariadb)
    implementation(Libs.testcontainers_mysql)
    implementation(Libs.testcontainers_postgresql)

    // Testcontainers 를 위한 DB 드라이버
    implementation(Libs.mariadb_java_client)
    implementation(Libs.mysql_connector_j)
    implementation(Libs.postgresql_driver)

    // Identifier 자동 생성
    implementation(Libs.bluetape4k_idgenerators)
    implementation(Libs.java_uuid_generator)

    // Coroutines
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactor)
    testImplementation(Libs.kotlinx_coroutines_debug)
    testImplementation(Libs.kotlinx_coroutines_test)

    // Kotlin Serialization Json
    implementation(platform(Libs.kotlinx_serialization_bom))
    implementation(Libs.kotlinx_serialization_json)

    // Java Money
    implementation(Libs.bluetape4k_money)
    implementation(Libs.javax_money_api)
    implementation(Libs.javamoney_moneta)

    // Logcaptor
    api(Libs.logcaptor)

}
