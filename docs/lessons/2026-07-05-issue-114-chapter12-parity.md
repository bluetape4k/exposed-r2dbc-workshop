# Issue #114 Chapter 12 Parity Refresh

## Context

Issue #114 revisited Chapter 12 after `exposed-workshop` added ten
topic-specific production-integration examples.

## Decision

Keep `exposed-r2dbc-workshop` Chapter 12 as two runtime modules:
`01-spring-production-integration` and `02-ktor-production-integration`. The
R2DBC teaching target is the Spring/Ktor runtime boundary plus
`suspendTransaction` repository slices, not one Gradle module per topic.

## Guardrail

Future Chapter 12 R2DBC work should extend package slices inside the two
existing modules unless a new issue proves that the module boundary itself is
the learning goal.

## Verification

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain`
