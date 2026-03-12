# Repository Guidelines

## Project Structure & Module Organization

This is a Gradle multi-module workshop for Exposed + R2DBC.

- Root build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `buildSrc/`.
- Shared utilities: `00-shared/exposed-r2dbc-shared`.
- Scenario groups: `01-spring-boot`, `03-exposed-r2dbc-basic`, `04-exposed-r2dbc-ddl`, `05-exposed-r2dbc-dml`,
  `06-advanced`, `07-jpa-convert`, `08-r2dbc-coroutines`, `09-spring`, `10-multi-tenant`, `11-high-performance`.
- Typical module layout: `src/main/kotlin`, `src/test/kotlin`, `src/main/resources`, `src/test/resources`.

## Build, Test, and Development Commands

Use the Gradle wrapper from repository root.

- `./gradlew clean build`: compile all modules, run tests, and assemble artifacts.
- `./gradlew test`: run all test suites on JUnit Platform.
- `./gradlew detekt`: run static analysis across modules.
- `./gradlew :exposed-r2dbc-09-spring-05-exposed-r2dbc-repository-coroutines:test`: run one module’s tests.
- `./gradlew :exposed-r2dbc-09-spring-07-spring-suspended-cache:bootRun`: run a Spring sample locally.
- `./bin/repo-status`: compact repository status summary for Codex sessions.
- `./bin/repo-diff`: compact diff summary with per-file churn instead of full patch output.
- `./bin/repo-test-summary -- ./gradlew <task>`: compact Gradle test/task summary with failure highlights.

## Coding Style & Naming Conventions

- Kotlin/Java toolchain: Kotlin 2.3, Java 21.
- Follow Kotlin official style (`kotlin.code.style=official`), 4-space indentation, and no tabs.
- Names: classes/objects `PascalCase`, functions/properties `camelCase`, constants `UPPER_SNAKE_CASE`.
- Test/example classes in this repository commonly use `ExNN_Description` or `*Test`.
- Public classes, interfaces, and extension functions should include KDoc (Korean preferred in this repository).

## Testing Guidelines

- Primary frameworks: JUnit 5, MockK, Kluent, kotlinx-coroutines-test, and Testcontainers.
- Keep DB tests deterministic and isolated; avoid shared mutable state between tests.
- Add or update tests for every behavior change, especially coroutine and transaction boundaries.
- During development, run module-targeted tests first, then run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines

- Use concise Conventional-style prefixes used in history: `feat:`, `fix:`, `refactor:`, `test:`, `doc:`, `chore:`,
  `build(deps):`.
- Keep each commit focused on one coherent change.
- PRs must include change purpose, affected module paths, executed test commands with results, and any config/migration notes.
