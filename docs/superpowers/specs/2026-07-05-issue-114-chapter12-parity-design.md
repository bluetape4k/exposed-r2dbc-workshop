# Issue #114 Chapter 12 R2DBC Parity Design

## Context

Issue [#114](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/114)
refreshes Chapter 12 parity against the current
`exposed-workshop/12-production-integration` layout. The source workshop now
has ten topic-specific Chapter 12 modules:

| Source example | Topic |
|---|---|
| `01-ktor-application-architecture` | Ktor application architecture |
| `02-spring-application-architecture` | Spring application architecture |
| `03-spring-http-outbox-idempotency` | Spring outbound HTTP outbox and idempotency |
| `04-ktor-http-outbox-idempotency` | Ktor outbound HTTP outbox and idempotency |
| `05-spring-auth-session` | Spring authentication and session metadata |
| `06-ktor-auth-session` | Ktor authentication and session metadata |
| `07-spring-outbox-realtime` | Spring realtime outbox |
| `08-ktor-outbox-realtime` | Ktor realtime outbox |
| `09-spring-observability-readiness` | Spring observability and readiness |
| `10-ktor-observability-readiness` | Ktor observability and readiness |

Current R2DBC evidence:

- `12-production-integration/README.md` and `README.ko.md` describe two leaf
  modules: `01-spring-production-integration` and
  `02-ktor-production-integration`.
- The original Issue #43 design deliberately chose two modules instead of ten
  topic modules, with child issues mapped to package-level slices.
- Lessons for issues #43, #45, #46, and #47 repeat the guardrail that future
  Chapter 12 work should extend the existing two modules unless the module
  boundary itself becomes the lesson.
- CodeGraph and local source inspection show the Spring and Ktor modules still
  include package slices for application structure, auth/session, realtime,
  outbound, diagnostics/readiness, and tests.
- `.github/workflows/Examples.yml` already runs both Chapter 12 R2DBC modules
  when `12-production-integration/**` changes.
- `gradle/libs.versions.toml` currently sets `spring-boot = "4.1.0"`, so the
  existing Chapter 12 README wording that says Spring Boot 4 is source-backed.
  The repo-local AGENTS project-doc version line is older than the current
  version catalog and is not changed by this issue.
- `gradle/libs.versions.toml` currently sets `kotlin = "2.4.0"`, so spec and
  plan version references use the catalog values rather than stale project-doc
  text.

## Problem

The R2DBC workshop already covers the Chapter 12 concepts, but the public
README table only maps old R2DBC issue numbers to package slices. It does not
explicitly map the current ten `exposed-workshop` source examples to the two
R2DBC modules. That makes future roadmap triage prone to reopening duplicate
module-split work.

## Design Decision

Keep the existing two-module R2DBC shape and add source-example parity tables
to the Chapter 12 README pair.

| Source workshop topic | R2DBC Spring counterpart | R2DBC Ktor counterpart | Decision |
|---|---|---|---|
| Application architecture | `01-spring-production-integration` package slices `app`, `persistence`, `web` | `02-ktor-production-integration` package slices `app`, `config`, `routes`, `persistence` | Covered by module-level Spring/Ktor pair |
| HTTP outbox/idempotency | Spring `outbound` slice with WebClient dispatch | Ktor `outbound` slice with Ktor HTTP client and MockEngine tests | Covered by package slices |
| Auth/session | Spring `auth` slice with WebFlux Security and DB session metadata | Ktor auth/session cookie flow backed by DB session metadata | Covered by package slices |
| Realtime outbox | Spring `realtime` SSE replay and persisted outbox state | Ktor WebSocket replay/live stream and persisted outbox state | Covered by package slices |
| Observability/readiness | Spring request correlation, diagnostics, readiness endpoints | Ktor CallId/CallLogging, diagnostics, readiness routes | Covered by package slices |

No new Gradle module is created for Issue #114. If the audit finds a missing
behavioral slice while updating the table, that gap becomes a narrow
implementation patch inside the existing Spring/Ktor module rather than a
module split.

## Alternatives Considered

### Approach A: Keep Two Modules And Add Explicit Parity Tables

This is the selected approach. It matches the Issue #43 design, preserves
existing CI coverage, and makes the ten source examples visible without adding
Gradle churn.

### Approach B: Split R2DBC Chapter 12 Into Ten Modules

Rejected. This contradicts the existing design and lessons, duplicates
framework wiring, expands workflow coverage without adding a new R2DBC concept,
and makes future maintenance noisier.

### Approach C: Leave README As-Is

Rejected. The current README says Chapter 12 is covered, but it does not map
the current source-workshop module names. That is enough ambiguity to cause
duplicate backlog issues.

## Scope

Required changes:

- Update `12-production-integration/README.md` with an explicit source-example
  parity table.
- Update `12-production-integration/README.ko.md` with the same mapping in
  Korean.
- Keep root README parity text consistent if the Chapter 12 row needs wording
  clarification.
- Add a lesson note for the Issue #114 parity refresh.

Out of scope:

- New Gradle modules.
- New dependencies.
- Real external services.
- Reworking existing Spring/Ktor code unless the audit finds a missing required
  behavior.

## Risks And Failure Modes

- Documentation drift: README could claim coverage that tests do not prove.
  Mitigation: map table entries to existing source paths and test names before
  finishing.
- Module-split regression: future contributors may assume ten source modules
  require ten R2DBC modules. Mitigation: state the two-module decision and
  rejected split directly in the README.
- Weak verification: docs-only changes can hide stale code references.
  Mitigation: run `./gradlew projects` and targeted Chapter 12 tests after the
  README update.
- Existing develop noise: the main checkout has unrelated uncommitted changes.
  Mitigation: all work happens in the clean `feat/issue-114-chapter12-parity`
  worktree.
- Version-source drift: repo guidance may mention an older Spring Boot line.
  Mitigation: keep Chapter 12 public docs aligned to `gradle/libs.versions.toml`
  and record the evidence in the review artifact.

## Acceptance Criteria

- `12-production-integration/README.md` and `README.ko.md` contain a parity
  table for all ten current `exposed-workshop` Chapter 12 examples.
- The table states why R2DBC keeps two modules instead of ten.
- Every table entry points to existing R2DBC module/slice coverage or records a
  concrete gap.
- `./gradlew projects` confirms Chapter 12 module discovery.
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain`
  passes.
- `docs/lessons/2026-07-05-issue-114-chapter12-parity.md` records the future
  guardrail.

## DoD

- GitHub Issue #114 remains linked to Epic #113.
- Spec and plan are committed before implementation.
- Step 2-R and Step 3-R review gates record P0/P1 = 0.
- Implementation diff is docs-first and excludes unrelated main-checkout
  changes.
- PR body ends with `## DoD Status` if a PR is created.
