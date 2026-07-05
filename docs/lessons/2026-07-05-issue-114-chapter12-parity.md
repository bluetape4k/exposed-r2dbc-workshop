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

README diagrams for Chapter 12 should stay source-backed: update both the
architecture parity map and caller sequence when parity tables, route flow, or
outbox/realtime semantics change, then rerender PNGs from SVG.

## Verification

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --rerun-tasks --console=plain`
- `~/.local/bin/cairosvg docs/assets/readme-diagrams/issue-114-chapter12-parity-architecture-01.svg -o docs/assets/readme-diagrams/issue-114-chapter12-parity-architecture-01.png -s 2`
- `~/.local/bin/cairosvg docs/assets/readme-diagrams/issue-114-chapter12-caller-sequence-01.svg -o docs/assets/readme-diagrams/issue-114-chapter12-caller-sequence-01.png -s 2`
