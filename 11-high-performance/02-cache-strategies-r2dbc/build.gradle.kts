import groovy.json.JsonSlurper
import java.io.File
import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
    id(Plugins.graalvm_native)
    id(Plugins.kotlinx_benchmark) version Plugins.Versions.kotlinx_benchmark
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
    implementation(platform(Libs.exposed_bom))
    implementation(project(":exposed-r2dbc-shared"))

    // Exposed
    implementation(Libs.exposed_core)
    implementation(Libs.exposed_r2dbc)
    implementation(Libs.exposed_java_time)
    implementation(Libs.exposed_kotlin_datetime)

    // bluetape4k
    implementation(Libs.bluetape4k_exposed)
    implementation(Libs.bluetape4k_exposed_r2dbc)
    implementation(Libs.bluetape4k_exposed_r2dbc_redisson)
    implementation(Libs.bluetape4k_idgenerators)
    implementation(Libs.bluetape4k_redis)
    implementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_junit5)

    // R2DBC Drivers
    runtimeOnly(Libs.h2_v2)

    runtimeOnly(Libs.r2dbc_spi)
    runtimeOnly(Libs.r2dbc_pool)
    runtimeOnly(Libs.r2dbc_h2)
    runtimeOnly(Libs.r2dbc_mysql)
    implementation(Libs.r2dbc_postgresql)

    // MySQL
    implementation(Libs.testcontainers_mysql)
    runtimeOnly(Libs.mysql_connector_j)

    // PostgreSQL
    implementation(Libs.testcontainers_postgresql)
    runtimeOnly(Libs.postgresql_driver)

    // Spring Boot
    implementation(Libs.springBoot("autoconfigure"))
    annotationProcessor(Libs.springBoot("autoconfigure-processor"))
    annotationProcessor(Libs.springBoot("configuration-processor"))
    runtimeOnly(Libs.springBoot("devtools"))

    implementation(Libs.springBootStarter("actuator"))
    implementation(Libs.springBootStarter("aop"))
    implementation(Libs.springBootStarter("validation"))
    implementation(Libs.springBootStarter("webflux"))

    testImplementation(Libs.bluetape4k_spring_tests)
    testImplementation(Libs.springBootStarter("test")) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Jackson
    implementation(Libs.bluetape4k_jackson)
    implementation(Libs.jackson_core)
    implementation(Libs.jackson_module_kotlin)
    implementation(Libs.jackson_module_blackbird)

    // Redisson Cache
    implementation(Libs.redisson)

    // Codecs
    runtimeOnly(Libs.fory_kotlin)
    runtimeOnly(Libs.kryo5)

    // Compressor
    runtimeOnly(Libs.lz4_java)
    runtimeOnly(Libs.snappy_java)
    runtimeOnly(Libs.zstd_jni)

    // Near Cache
    implementation(Libs.caffeine)

    implementation(Libs.datafaker)

    // Coroutines
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_reactor)
    testImplementation(Libs.kotlinx_coroutines_test)
    add("benchmarkImplementation", Libs.kotlinx_benchmark_runtime)
    add("benchmarkImplementation", Libs.kotlinx_benchmark_runtime_jvm)

    // Reactor
    implementation(Libs.reactor_netty)
    implementation(Libs.reactor_kotlin_extensions)
    testImplementation(Libs.reactor_test)

    // SpringDoc - OpenAPI 3.0
    implementation(Libs.springdoc_openapi_starter_webflux_ui)
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
