plugins {
    alias(libs.plugins.exposed)
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graalvm.native)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.suspendedcache"
        databaseUrl = "jdbc:h2:mem:09-spring-07-spring-suspended-cache-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
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

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {

    implementation(project(":exposed-r2dbc-shared"))

    // Exposed
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)

    // bluetape4k
    implementation(libs.bluetape4k.r2dbc)
    implementation(libs.exposed.r2dbc)
    implementation(libs.bluetape4k.redis)
    implementation(libs.bluetape4k.grpc)
    implementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.bluetape4k.junit5)

    // R2DBC Drivers
    runtimeOnly(libs.h2.v2)

    runtimeOnly(libs.r2dbc.spi)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.mysql)
    implementation(libs.r2dbc.postgresql)

    // MySQL
    implementation(libs.testcontainers.mysql)
    runtimeOnly(libs.mysql.connector.j)

    // PostgreSQL
    implementation(libs.testcontainers.postgresql)
    runtimeOnly(libs.postgresql.driver)

    // Spring Boot 통합 의존성
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    runtimeOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation(libs.bluetape4k.spring.boot.core)
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }
    testImplementation(libs.mockk)

    // Redis 캐시 의존성 Cache
    implementation(libs.lettuce.core)
    runtimeOnly(libs.commons.pool2)

    // Codecs
    runtimeOnly(libs.fory.kotlin)
    runtimeOnly(libs.kryo)

    // Compressor
    runtimeOnly(libs.lz4.java)
    runtimeOnly(libs.snappy.java)
    runtimeOnly(libs.zstd.jni)

    // 코루틴 지원 의존성
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Reactor 연동 의존성
    implementation(libs.reactor.netty)
    implementation(libs.reactor.kotlin.extensions)
    testImplementation(libs.reactor.test)

    // SpringDoc - OpenAPI 3.0
    implementation(libs.springdoc.openapi.starter.webflux.ui)
}
