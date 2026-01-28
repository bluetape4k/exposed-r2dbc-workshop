configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    testImplementation(project(":exposed-r2dbc-shared"))

    // Exposed
    testImplementation(Libs.exposed_r2dbc)
    testImplementation(Libs.exposed_java_time)

    testImplementation(Libs.bluetape4k_exposed_r2dbc)
    testImplementation(Libs.bluetape4k_junit5)

    testRuntimeOnly(Libs.h2_v2)

    testRuntimeOnly(Libs.r2dbc_spi)
    testRuntimeOnly(Libs.r2dbc_pool)
    testRuntimeOnly(Libs.r2dbc_h2)
    testRuntimeOnly(Libs.r2dbc_mariadb)
    testRuntimeOnly(Libs.r2dbc_mysql)
    testRuntimeOnly(Libs.r2dbc_postgresql)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_junit_jupiter)
    testImplementation(Libs.testcontainers_mariadb)
    testImplementation(Libs.testcontainers_mysql)
    testImplementation(Libs.testcontainers_postgresql)

    // Testcontainers 를 위한 DB 드라이버
    testRuntimeOnly(Libs.mariadb_java_client)
    testRuntimeOnly(Libs.mysql_connector_j)
    testRuntimeOnly(Libs.postgresql_driver)

    // Coroutines
    testImplementation(Libs.bluetape4k_coroutines)
    testImplementation(Libs.kotlinx_coroutines_core)
    testImplementation(Libs.kotlinx_coroutines_debug)
    testImplementation(Libs.kotlinx_coroutines_test)
}
