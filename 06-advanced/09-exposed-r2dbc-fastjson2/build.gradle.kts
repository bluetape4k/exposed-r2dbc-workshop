configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    implementation(project(":exposed-r2dbc-shared"))

    // Exposed
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_r2dbc)

    implementation(Libs.bluetape4k_io)

    // bluetape4k_exposed 가 사용하는 exposed_core 의 버전 및 namespace 가 다르므로, 참조하면 안됩니다.
    // implementation(Libs.bluetape4k_exposed)
    implementation(Libs.bluetape4k_fastjson2)

    implementation(Libs.bluetape4k_junit5)

    implementation(Libs.h2_v2)

    implementation(Libs.r2dbc_spi)
    implementation(Libs.r2dbc_pool)
    implementation(Libs.r2dbc_h2)
    implementation(Libs.r2dbc_mariadb)
    implementation(Libs.r2dbc_mysql)
    implementation(Libs.r2dbc_postgresql)

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

    // Coroutines
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactive)
    implementation(Libs.kotlinx_coroutines_debug)
    implementation(Libs.kotlinx_coroutines_test)
}
