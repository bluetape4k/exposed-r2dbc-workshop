plugins {
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graalvm.native)
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
    implementation(platform(libs.jetbrains.exposed.bom))

    // Exposed
    implementation(libs.jetbrains.exposed.core)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.exposed.r2dbc)

    // R2DBC
    runtimeOnly(libs.h2.v2)
    implementation(libs.r2dbc.spi)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.h2)

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Coroutines / Reactor
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.reactor.kotlin.extensions)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.spring.boot.core)
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }
}
