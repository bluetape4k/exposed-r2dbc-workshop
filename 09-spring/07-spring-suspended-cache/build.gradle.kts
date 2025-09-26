plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
    id(Plugins.graalvm_native)
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.suspendedcache.SpringSuspendedCacheApplicationKt")

    buildInfo {
        properties {
            additional.put("name", "Exposed SuspendedCache Application")
            additional.put("description", "Exposed 와 SuspendedCacheRepository를 이용하여 비동기방식으로 DB 및 Redis에 접근하는 예제")
            version = "1.0.0"
            additional.put("java.version", JavaVersion.current())
        }
    }
}

@Suppress("UnstableApiUsage")
configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    implementation(project(":exposed-r2dbc-shared"))

    // bluetape4k
    implementation(Libs.bluetape4k_exposed_r2dbc)
    implementation(Libs.bluetape4k_redis)
    implementation(Libs.bluetape4k_grpc)
    implementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_spring_tests)

    // Exposed
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.exposed_java_time)

    // R2DBC Drivers
    implementation(Libs.h2_v2)

    implementation(Libs.r2dbc_spi)
    implementation(Libs.r2dbc_pool)
    implementation(Libs.r2dbc_h2)
    implementation(Libs.r2dbc_mysql)
    implementation(Libs.r2dbc_postgresql)

    // MySQL
    implementation(Libs.bluetape4k_testcontainers)
    implementation(Libs.testcontainers_mysql)
    implementation(Libs.mysql_connector_j)

    // PostgreSQL
    implementation(Libs.bluetape4k_testcontainers)
    implementation(Libs.testcontainers_postgresql)
    implementation(Libs.postgresql_driver)

    // Spring Boot
    implementation(Libs.springBoot("autoconfigure"))
    annotationProcessor(Libs.springBoot("autoconfigure-processor"))
    annotationProcessor(Libs.springBoot("configuration-processor"))
    runtimeOnly(Libs.springBoot("devtools"))

    implementation(Libs.springBootStarter("webflux"))
    implementation(Libs.springBootStarter("aop"))
    implementation(Libs.springBootStarter("actuator"))
    implementation(Libs.springBootStarter("validation"))

    testImplementation(Libs.bluetape4k_spring_tests)
    testImplementation(Libs.springBootStarter("test")) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Redis Cache
    implementation(Libs.lettuce_core)
    implementation(Libs.commons_pool2)

    // Codecs
    implementation(Libs.fory_kotlin)
    // Compressor
    implementation(Libs.lz4_java)

    // Coroutines
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactive)
    implementation(Libs.kotlinx_coroutines_reactor)
    testImplementation(Libs.kotlinx_coroutines_test)

    // Reactor
    implementation(Libs.reactor_netty)
    implementation(Libs.reactor_kotlin_extensions)
    testImplementation(Libs.reactor_test)

    // Monitoring
    implementation(Libs.micrometer_core)
    implementation(Libs.micrometer_registry_prometheus)

    // SpringDoc - OpenAPI 3.0
    implementation(Libs.springdoc_openapi_starter_webflux_ui)
}
