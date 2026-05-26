plugins {
    alias(libs.plugins.exposed)
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graalvm.native)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.multitenant.security"
        databaseUrl = "jdbc:h2:mem:10-multi-tenant-05-spring-security-tenant-authorization-spring-webflux-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}


springBoot {
    mainClass.set("exposed.r2dbc.multitenant.security.SecurityTenantAppKt")

    buildInfo {
        properties {
            additional.put("name", "Spring Security tenant authorization WebFlux Example")
            additional.put("description", "Spring WebFlux + Exposed R2DBC Spring Security tenant authorization example")
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

    // Exposed
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.jetbrains.exposed.migration.r2dbc)

    // bluetape4k
    implementation(libs.exposed.r2dbc)
    testImplementation(libs.bluetape4k.junit5)

    // R2DBC Drivers
    runtimeOnly(libs.h2.v2)

    implementation(libs.r2dbc.spi)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.h2)

    // Spring Boot
    implementation(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    runtimeOnly(libs.spring.boot.devtools)

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aspectj)
    implementation(libs.spring.boot.starter.data.r2dbc)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webflux)

    testImplementation(libs.bluetape4k.spring.boot.core)
    testImplementation(libs.spring.boot.webtestclient)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Redis Cache
    runtimeOnly(libs.lettuce.core)
    runtimeOnly(libs.commons.pool2)

    // Codecs
    runtimeOnly(libs.fory.kotlin)
    runtimeOnly(libs.kryo)

    // Compressor
    runtimeOnly(libs.lz4.java)
    runtimeOnly(libs.snappy.java)
    runtimeOnly(libs.zstd.jni)

    // Coroutines
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    // Reactor
    implementation(libs.reactor.netty)
    implementation(libs.reactor.kotlin.extensions)
    testImplementation(libs.reactor.test)

}
