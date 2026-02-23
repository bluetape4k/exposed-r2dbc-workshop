plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
    id(Plugins.graalvm_native)
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.routing.RoutingR2dbcApplicationKt")

    buildInfo {
        properties {
            additional.put("name", "Routing DataSource Example")
            additional.put("description", "Exposed R2DBC + Spring WebFlux Routing Example")
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

    // Exposed
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.bluetape4k_exposed_r2dbc)

    // R2DBC
    runtimeOnly(Libs.h2_v2)
    implementation(Libs.r2dbc_spi)
    implementation(Libs.r2dbc_pool)
    implementation(Libs.r2dbc_h2)

    // Spring Boot
    implementation(Libs.springBoot("autoconfigure"))
    annotationProcessor(Libs.springBoot("autoconfigure-processor"))
    annotationProcessor(Libs.springBoot("configuration-processor"))

    implementation(Libs.springBootStarter("actuator"))
    implementation(Libs.springBootStarter("data-r2dbc"))
    implementation(Libs.springBootStarter("webflux"))
    implementation(Libs.springBootStarter("validation"))

    // Coroutines / Reactor
    implementation(Libs.kotlinx_coroutines_reactor)
    implementation(Libs.reactor_kotlin_extensions)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_spring_tests)
    testImplementation(Libs.springBootStarter("test")) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }
}
