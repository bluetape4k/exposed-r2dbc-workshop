# Dependency Catalog Upgrades

## Context

`bluetape4k-dependencies` folded the Apache Fory Dependabot PRs into the
central dependency upgrade batch.

## Decision

Materialize the central Fory Kotlin catalog version in this workshop repository.

## Outcome

`gradle/libs.versions.toml` now carries Fory Kotlin `0.17.0`.

## Verification

- `./gradlew build -x test --no-daemon`
