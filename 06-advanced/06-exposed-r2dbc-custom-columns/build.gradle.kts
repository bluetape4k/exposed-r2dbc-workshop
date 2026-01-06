configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    implementation(project(":exposed-r2dbc-shared"))

    // Exposed
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.bluetape4k_exposed)
    implementation(Libs.bluetape4k_exposed_r2dbc)
    testImplementation(Libs.bluetape4k_junit5)

    testImplementation(Libs.bluetape4k_io)

    // Compression
    testImplementation(Libs.lz4_java)
    testImplementation(Libs.snappy_java)
    testImplementation(Libs.zstd_jni)

    // Serialization
    testImplementation(Libs.fory_kotlin)
    testImplementation(Libs.kryo5)

    // Encryption
    testImplementation(Libs.bluetape4k_crypto)
    testImplementation(Libs.jasypt)

    // Identifier 자동 생성
    implementation(Libs.bluetape4k_idgenerators)
    implementation(Libs.java_uuid_generator)

    testRuntimeOnly(Libs.h2_v2)

    testRuntimeOnly(Libs.r2dbc_spi)
    testRuntimeOnly(Libs.r2dbc_pool)
    testRuntimeOnly(Libs.r2dbc_h2)
    testRuntimeOnly(Libs.r2dbc_mariadb)
    testRuntimeOnly(Libs.r2dbc_mysql)
    testRuntimeOnly(Libs.r2dbc_postgresql)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
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
