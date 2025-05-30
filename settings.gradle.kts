pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
        id("org.gradle.toolchains.foojay-resolver-convention") version ("0.10.0")
    }
}

val PROJECT_NAME = "exposed-r2dbc"

rootProject.name = "$PROJECT_NAME-workshop"

includeModules("00-shared", false, false)

includeModules("01-spring-boot", false, false)
includeModules("02-alternatives-to-jpa", false, false)
includeModules("03-exposed-r2dbc-basic", false, false)
includeModules("04-exposed-r2dbc-ddl", false, false)
includeModules("05-exposed-r2dbc-dml", false, false)
includeModules("06-advanced", false, false)
includeModules("07-jpa-convert", false, false)
includeModules("08-r2dbc-coroutines", false, false)
includeModules("09-spring", false, false)
includeModules("10-multi-tenant", false, false)
includeModules("11-high-performance", false, false)

fun includeModules(baseDir: String, withProjectName: Boolean = true, withBaseDir: Boolean = true) {
    files("$rootDir/$baseDir").files
        .filter { it.isDirectory }
        .forEach { moduleDir ->
            moduleDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    val basePath = baseDir.replace("/", "-")
                    val projectName = when {
                        !withProjectName && !withBaseDir -> dir.name
                        withProjectName && !withBaseDir -> PROJECT_NAME + "-" + dir.name
                        withProjectName -> PROJECT_NAME + "-" + basePath + "-" + dir.name
                        else -> basePath + "-" + dir.name
                    }
                    // println("include modules: $projectName")

                    include(projectName)
                    project(":$projectName").projectDir = dir
                }
        }
}
