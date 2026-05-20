# Dependency Catalog Upgrades

## Context

`bluetape4k-dependencies` folded the Apache Fory Dependabot PRs into the
central dependency upgrade batch. The same batch also moved the central
bluetape4k BOM constraints to the `1.8.1-SNAPSHOT` family.

## Decision

Materialize the central Fory Kotlin catalog version and align direct bluetape4k
snapshot aliases with the published `1.8.1-SNAPSHOT` modules.

## Outcome

`gradle/libs.versions.toml` now carries Fory Kotlin `0.17.0` and direct
bluetape4k aliases resolve against `1.8.1-SNAPSHOT`.

## Verification

- `./gradlew build -x test --no-daemon`
- `./gradlew :02-cache-strategies-r2dbc:dependencyInsight --configuration testRuntimeClasspath --dependency bluetape4k-io --refresh-dependencies --no-daemon --no-configuration-cache`
- `./gradlew :02-cache-strategies-r2dbc:test -PuseDB=H2 --refresh-dependencies --no-daemon --no-configuration-cache`
