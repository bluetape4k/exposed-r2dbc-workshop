configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(libs.exposed.bom))

    testImplementation(project(":exposed-r2dbc-shared"))

    // Exposed
    testImplementation(libs.exposed.r2dbc)
    testImplementation(libs.bluetape4k.exposed.r2dbc)

    // Java 21 에서 Virtual Thread 를 사용할 때 (Java 25 에서는 jdk25 를 사용하세요)
    testRuntimeOnly(libs.bluetape4k.virtualthread.jdk21)
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
    testImplementation(libs.mariadb.java.client)
    testImplementation(libs.mysql.connector.j)
    testImplementation(libs.postgresql.driver)

    // Coroutines
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)
}
