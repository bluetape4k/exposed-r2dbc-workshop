plugins {
    alias(libs.plugins.exposed)
}

exposed {
    migrations {
        tablesPackage = "exposed.r2dbc.examples.ddd.aggregate"
        databaseUrl = "jdbc:h2:mem:13-ecosystem-integrations-07-ddd-aggregate-repository-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        databaseUser = "sa"
        databasePassword = ""
    }
}

dependencies {
    implementation(project(":exposed-r2dbc-shared"))
    implementation(libs.exposed.core)
    implementation(libs.bluetape4k.idgenerators)
    implementation(libs.jetbrains.exposed.r2dbc)
    implementation(libs.exposed.r2dbc)

    runtimeOnly(libs.h2.v2)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.pool)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
