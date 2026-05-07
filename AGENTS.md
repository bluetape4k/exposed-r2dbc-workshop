# AGENTS.md - exposed-r2dbc-workshop

Kotlin-only Exposed R2DBC learning workshop. Do not add Java code.

- Kotlin 2.3.20
- Spring Boot 3.5.11
- Exposed 1.1.1
- bluetape4k 1.5.0-Beta1
- JDK 21

## Commands

```bash
./gradlew build
./gradlew test
./gradlew :exposed-r2dbc-shared:test
./gradlew :spring-webflux-exposed:test
./gradlew :01-dml:test
./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"
./gradlew clean
repo-status
repo-diff
repo-test-summary -- ./gradlew :05-exposed-dml:01-dml:test
```

Gradle module names are leaf directory names.

## Layout

```text
00-shared/exposed-r2dbc-shared/
01-spring-boot/spring-webflux-exposed/
02-alternatives-to-jpa/
03-exposed-r2dbc-basic/
04-exposed-r2dbc-ddl/
05-exposed-r2dbc-dml/
06-advanced/
07-jpa-convert/
08-r2dbc-coroutines/
09-spring/
10-multi-tenant/
11-high-performance/
```

## Shared Test Infrastructure

- Tests extend `AbstractR2dbcExposedTest`.
- `TestDB`: H2, H2_MYSQL, H2_PSQL, MARIADB, MYSQL_V8, POSTGRESQL.
- `USE_FAST_DB=true` limits runs to H2.
- Use `withDb(testDB) { }` and `withTables(testDB, *tables) { }` for lifecycle
  and transaction management.
- Multi-module tests are serialized through a BuildService mutex.
- All tests use UTC timezone.

## Exposed R2DBC Rules

- Use packages under `org.jetbrains.exposed.v1.core` and
  `org.jetbrains.exposed.v1.r2dbc`.
- All DB access must run inside `suspendTransaction` or shared helpers.
- `selectAll()` returns a Flow; collect it intentionally.
- General classes use `companion object : KLogging()`.
- Coroutine-heavy code can use `companion object : KLoggingChannel()`.
- New dependencies go through `buildSrc/src/main/kotlin/Libs.kt`.

## Project Skills

If available, use the local `exposed-r2dbc` skill for `withDb`, `withTables`,
`suspendTransaction`, table definitions, Flow DML APIs, and multi-DB
parameterized tests.
