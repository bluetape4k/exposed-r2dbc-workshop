plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
    id(Plugins.graalvm_native)
    id(Plugins.gatling) version Plugins.Versions.gatling
}


springBoot {
    mainClass.set("exposed.r2dbc.workshop.springwebflux.SpringWebfluxApplicationKt")

    buildInfo {
        properties {
            additional.put("name", "Webflux + Exposed Application")
            additional.put("description", "Webflux + Exposed Application")
            version = "1.0.0"
            additional.put("java.version", JavaVersion.current())
        }
    }
}


configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(platform(Libs.exposed_bom))

    implementation(project(":exposed-r2dbc-shared"))

    // bluetape4k
    implementation(Libs.bluetape4k_exposed_r2dbc)
    testImplementation(Libs.bluetape4k_junit5)
    implementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_spring_tests)

    // Exposed
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_java_time)

    runtimeOnly(Libs.h2_v2)

    runtimeOnly(Libs.r2dbc_spi)
    runtimeOnly(Libs.r2dbc_pool)
    runtimeOnly(Libs.r2dbc_h2)
    runtimeOnly(Libs.r2dbc_mariadb)
    runtimeOnly(Libs.r2dbc_mysql)
    implementation(Libs.r2dbc_postgresql)

    // MySQL
    implementation(Libs.testcontainers_mysql)
    implementation(Libs.mysql_connector_j)

    // PostgreSQL
    implementation(Libs.testcontainers_postgresql)
    implementation(Libs.postgresql_driver)

    // Spring Boot
    implementation(Libs.springBoot("autoconfigure"))
    annotationProcessor(Libs.springBoot("autoconfigure-processor"))
    annotationProcessor(Libs.springBoot("configuration-processor"))
    runtimeOnly(Libs.springBoot("devtools"))

    implementation(Libs.springBootStarter("actuator"))
    implementation(Libs.springBootStarter("aop"))
    implementation(Libs.springBootStarter("data-r2dbc"))
    implementation(Libs.springBootStarter("validation"))
    implementation(Libs.springBootStarter("webflux"))

    testImplementation(Libs.bluetape4k_spring_tests)
    testImplementation(Libs.springBootStarter("test")) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Coroutines
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
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

    // Gatling
    implementation(Libs.gatling_app)
    implementation(Libs.gatling_core_java)
    implementation(Libs.gatling_http_java)
    implementation(Libs.gatling_recorder)
    implementation(Libs.gatling_charts_highcharts)
    testImplementation(Libs.gatling_test_framework)
}
