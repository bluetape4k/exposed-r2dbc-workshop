plugins {
    alias(libs.plugins.exposed)
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graalvm.native)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.springbootrepository"
        databaseUrl = "jdbc:h2:mem:09-spring-06-exposed-spring-boot-r2dbc-repository-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.springbootrepository.ExposedSpringBootR2dbcRepositoryAppKt")

    buildInfo {
        properties {
            additional.put("name", "Spring Boot Exposed R2DBC Repository Application")
            additional.put("description", "Spring Boot repository scanning with Exposed R2DBC")
            version = "1.0.0"
            additional.put("java.version", JavaVersion.current())
        }
    }
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))

    // Spring Boot Exposed R2DBC repository provider
    implementation(libs.bluetape4k.exposed.spring.boot.r2dbc)

    // Exposed R2DBC
    implementation(libs.bluetape4k.r2dbc)
    implementation(libs.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.core)

    // R2DBC runtime
    runtimeOnly(libs.r2dbc.spi)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.h2.v2)

    // Spring Boot
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    runtimeOnly(libs.spring.boot.devtools)

    // Coroutines and WebFlux bridge
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    // Tests
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.spring.boot.core)
    testImplementation(libs.spring.boot.webtestclient)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }
    testImplementation(libs.mockk)
}
