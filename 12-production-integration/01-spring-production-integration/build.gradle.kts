plugins {
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.production.spring.SpringProductionIntegrationApplicationKt")
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))

    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.exposed.r2dbc)

    implementation(libs.kotlinx.coroutines.reactor)

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.pool)
    runtimeOnly(libs.r2dbc.spi)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
}
