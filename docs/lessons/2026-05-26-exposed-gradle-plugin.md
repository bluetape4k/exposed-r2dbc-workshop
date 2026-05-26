## Context

Adopted the JetBrains Exposed Gradle plugin across R2DBC workshop modules that define tables in main sources.

## Decision

The workshop uses a repo-local plugin alias tied to the existing Exposed version alias, not the managed `bt4k` catalog.

## Outcome

Spring WebFlux, multi-tenant, cache, routing, Ktor, and production examples now expose `generateMigrations` with explicit migration settings.

## Verification

Ran `git diff --check`, `./gradlew -q help`, and `:spring-webflux-exposed:tasks --all`.

## Future Guard

Use an H2 JDBC migration database for plugin task discovery even when the example runtime path is R2DBC.
