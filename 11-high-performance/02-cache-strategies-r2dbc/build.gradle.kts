import groovy.json.JsonSlurper
import java.time.Instant

plugins {
    alias(libs.plugins.exposed)
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlinx.benchmark)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.cache"
        databaseUrl = "jdbc:h2:mem:11-high-performance-02-cache-strategies-r2dbc-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

springBoot {
    mainClass.set("exposed.r2dbc.examples.cache.CacheStrategyApplicationKt")

    buildInfo {
        properties {
            additional.put("name", "Cache Strategy Application")
            additional.put("description", "Cache Strategies with Redisson and Exposed R2dbc")
            version = "1.0.0"
            additional.put("java.version", JavaVersion.current())
        }
    }
}

sourceSets {
    create("benchmark") {
        kotlin.srcDir("src/benchmark/kotlin")
        resources.srcDir("src/benchmark/resources")
    }
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
    named("benchmarkImplementation") {
        extendsFrom(implementation.get(), compileOnly.get())
    }
    named("benchmarkRuntimeOnly") {
        extendsFrom(runtimeOnly.get())
    }
}

kotlin {
    target {
        compilations.getByName("benchmark")
            .associateWith(compilations.getByName("main"))
    }
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))

    // Exposed
    implementation(libs.jetbrains.exposed.core)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.jetbrains.exposed.java.time)
    implementation(libs.jetbrains.exposed.kotlin.datetime)

    // bluetape4k
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.r2dbc.redisson)
    implementation(libs.bluetape4k.idgenerators)
    implementation(libs.bluetape4k.redis)
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

    // Spring Boot
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

    // Jackson
    implementation(libs.bluetape4k.jackson2)
    implementation(libs.jackson.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.blackbird)

    // Redisson Cache
    implementation(libs.redisson)

    // Codecs
    runtimeOnly(libs.fory.kotlin)
    runtimeOnly(libs.kryo)

    // Compressor
    runtimeOnly(libs.lz4.java)
    runtimeOnly(libs.snappy.java)
    runtimeOnly(libs.zstd.jni)

    // Near Cache
    implementation(libs.caffeine)

    implementation(libs.datafaker)

    // Coroutines
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)
    add("benchmarkImplementation", libs.kotlinx.benchmark.runtime)
    add("benchmarkImplementation", libs.kotlinx.benchmark.runtime.jvm)

    // Reactor
    implementation(libs.reactor.netty)
    implementation(libs.reactor.kotlin.extensions)
    testImplementation(libs.reactor.test)

    // SpringDoc - OpenAPI 3.0
    implementation(libs.springdoc.openapi.starter.webflux.ui)
}

benchmark {
    targets {
        register("benchmark")
    }
    configurations {
        named("main") {
            include(".*CacheStrategyRepositoryBenchmark.*")
            warmups = 5
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
        }
        register("smoke") {
            include(".*CacheStrategyRepositoryBenchmark.*")
            warmups = 2
            iterations = 3
            iterationTime = 250
            iterationTimeUnit = "ms"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
        }
    }
}

tasks.withType<JavaExec>().matching {
    it.name in setOf("benchmarkBenchmark", "benchmarkSmokeBenchmark")
}.configureEach {
    systemProperty("jmh.ignoreLock", "true")
}

abstract class BenchmarkMarkdownTask: DefaultTask() {
    @get:Input
    abstract val profile: org.gradle.api.provider.Property<String>

    @TaskAction
    fun generate() {
        val currentProfile = profile.get()
        val reportRoot = project.layout.buildDirectory.dir("reports/benchmarks/$currentProfile").get().asFile
        require(reportRoot.exists()) { "Benchmark report directory does not exist: ${reportRoot.absolutePath}" }

        val latestRunDir = reportRoot.listFiles()
            ?.filter(File::isDirectory)
            ?.maxByOrNull(File::lastModified)
            ?: error("No benchmark run directory found under ${reportRoot.absolutePath}")

        val jsonFile = latestRunDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.maxByOrNull(File::lastModified)
            ?: error("No benchmark JSON report found under ${latestRunDir.absolutePath}")

        val entries = JsonSlurper().parse(jsonFile) as? List<Map<String, *>>
            ?: error("Unexpected benchmark JSON format: ${jsonFile.absolutePath}")

        val markdown = buildString {
            appendLine("# Cache Strategy Kotlin Benchmark")
            appendLine()
            appendLine("- profile: `$currentProfile`")
            appendLine("- generatedAt: `${Instant.now()}`")
            appendLine("- source: `${jsonFile.relativeTo(project.projectDir)}`")
            appendLine()
            appendLine("| Benchmark | Mode | Score | Error | Unit | Params |")
            appendLine("| --- | --- | ---: | ---: | --- | --- |")
            entries.sortedBy { it["benchmark"].toString() }.forEach { row ->
                val params = (row["params"] as? Map<*, *>)
                    ?.entries
                    ?.joinToString(", ") { (key, value) -> "$key=$value" }
                    ?.ifBlank { "-" }
                    ?: "-"
                val primaryMetric = row["primaryMetric"] as? Map<*, *>
                val score = (primaryMetric?.get("score") as? Number)?.toDouble()?.let { "%.3f".format(it) } ?: "-"
                val error = (primaryMetric?.get("scoreError") as? Number)?.toDouble()?.let { "%.3f".format(it) } ?: "-"
                val unit = primaryMetric?.get("scoreUnit")?.toString() ?: "-"
                appendLine("| ${row["benchmark"]} | ${row["mode"]} | $score | $error | $unit | $params |")
            }
        }

        val outputFile = project.layout.buildDirectory.file(
            "reports/benchmarks/$currentProfile/benchmark-summary.md"
        ).get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(markdown)

        logger.lifecycle("Saved benchmark markdown report to ${outputFile.absolutePath}")
    }
}

tasks.register<BenchmarkMarkdownTask>("saveMainBenchmarkMarkdown") {
    group = "benchmark"
    description = "main 프로파일 실행 결과를 Markdown 리포트로 저장합니다."
    notCompatibleWithConfigurationCache("벤치마크 결과 파일을 실행 시점에 탐색합니다.")
    profile.set("main")
    dependsOn("benchmarkBenchmark")
}

tasks.register<BenchmarkMarkdownTask>("saveSmokeBenchmarkMarkdown") {
    group = "benchmark"
    description = "smoke 프로파일 실행 결과를 Markdown 리포트로 저장합니다."
    notCompatibleWithConfigurationCache("벤치마크 결과 파일을 실행 시점에 탐색합니다.")
    profile.set("smoke")
    dependsOn("benchmarkSmokeBenchmark")
}

tasks.register("kotlinBenchmarkMarkdown") {
    group = "benchmark"
    description = "smoke 프로파일로 Kotlin benchmark를 실행하고 Markdown 리포트를 생성합니다."
    dependsOn("saveSmokeBenchmarkMarkdown")
}
