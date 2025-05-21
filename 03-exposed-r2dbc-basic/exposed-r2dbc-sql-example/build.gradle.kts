configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    implementation(project(":exposed-r2dbc-shared"))

    // Bluetape4k Exposed
    api(Libs.bluetape4k_exposed)

    // Exposed
    api(Libs.exposed_core)
    api(Libs.exposed_r2dbc)
    implementation(Libs.exposed_java_time)

    api(Libs.bluetape4k_jdbc)
    api(Libs.bluetape4k_r2dbc)
    api(Libs.bluetape4k_junit5)

    api(Libs.h2_v2)

    api(Libs.r2dbc_spi)
    api(Libs.r2dbc_pool)
    api(Libs.r2dbc_h2)
    api(Libs.r2dbc_mariadb)
    api(Libs.r2dbc_mysql)
    api(Libs.r2dbc_postgresql)

    api(Libs.bluetape4k_testcontainers)
    api(Libs.testcontainers)
    api(Libs.testcontainers_junit_jupiter)
    api(Libs.testcontainers_mariadb)
    api(Libs.testcontainers_mysql)
    api(Libs.testcontainers_postgresql)

    // Testcontainers 를 위한 DB 드라이버
    api(Libs.mariadb_java_client)
    api(Libs.mysql_connector_j)
    api(Libs.postgresql_driver)

    // Coroutines
    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_reactive)
    api(Libs.kotlinx_coroutines_debug)
    api(Libs.kotlinx_coroutines_test)
}
