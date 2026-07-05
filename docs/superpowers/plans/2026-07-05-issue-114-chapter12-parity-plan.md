# Issue #114 Chapter 12 R2DBC Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current ten `exposed-workshop` Chapter 12 examples explicitly traceable to the two existing R2DBC Chapter 12 modules.

**Architecture:** Keep the established two-module R2DBC design. Add explicit README parity mapping and a lesson note; only touch production/test code if the parity audit finds a real missing behavior.

**Tech Stack:** Kotlin 2.4.0 and Spring Boot 4.1.0 from `gradle/libs.versions.toml`, Ktor 3, Exposed R2DBC, Gradle, bluetape4k assertions/test infrastructure.

---

## File Structure

- Modify: `12-production-integration/README.md`
  - Responsibility: English Chapter 12 concept and source-example parity map.
- Modify: `12-production-integration/README.ko.md`
  - Responsibility: Korean Chapter 12 concept and source-example parity map.
- Create: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md`
  - Responsibility: durable lesson for future Chapter 12 parity triage.
- Maybe modify: `README.md`, `README.ko.md`
  - Responsibility: root parity wording only if the Chapter 12 row is stale after the chapter README update.

No Kotlin source file is planned for modification unless Task 2 finds a table
entry with no current source/test counterpart.

## Task 1: Lock The Current Evidence

**Files:**
- Read: `12-production-integration/README.md`
- Read: `12-production-integration/README.ko.md`
- Read: `12-production-integration/01-spring-production-integration/src/test/kotlin/exposed/r2dbc/examples/production/spring/SpringProductionIntegrationApplicationTest.kt`
- Read: `12-production-integration/02-ktor-production-integration/src/test/kotlin/exposed/r2dbc/examples/production/ktor/KtorProductionIntegrationApplicationTest.kt`
- Read: `/Users/debop/work/bluetape4k/exposed-workshop/12-production-integration/README.md`

- [x] **Step 1: Re-run the source/current mapping commands**

Run:

```bash
find /Users/debop/work/bluetape4k/exposed-workshop/12-production-integration -maxdepth 2 -type d | sort
find 12-production-integration -maxdepth 2 -type d | sort
rg -n 'application architecture|HTTP Client Outbox|Authentication And Sessions|Realtime Outbox|Observability And Readiness' 12-production-integration/README.md 12-production-integration/README.ko.md
rg -n 'class .*Test|fun `' 12-production-integration/01-spring-production-integration/src/test/kotlin 12-production-integration/02-ktor-production-integration/src/test/kotlin
rg -n 'kotlin = |spring-boot = ' gradle/libs.versions.toml
```

Expected:

- Source workshop lists `01-*` through `10-*`.
- R2DBC workshop lists only `01-spring-production-integration` and `02-ktor-production-integration`.
- Chapter README pair already names the five production topics.
- Tests include auth/session, realtime, outbound/idempotency, diagnostics/readiness, and request-id cases.
- `gradle/libs.versions.toml` confirms the Kotlin and Spring Boot lines named
  in the spec and plan.

- [x] **Step 2: Record any actual gap before editing docs**

If a source topic lacks a current R2DBC counterpart, stop and add a narrow code/test task before Task 3. Otherwise continue with docs-only implementation.

## Task 2: Update English Chapter README

**Files:**
- Modify: `12-production-integration/README.md`

- [x] **Step 1: Insert a `Source Example Parity` section after `## Topic Map`**

Add this table:

```markdown
## Source Example Parity

`exposed-workshop` keeps Chapter 12 as ten focused modules. This R2DBC
workshop intentionally keeps two modules, one per runtime stack, and maps each
source example to package-level slices inside those modules.

| `exposed-workshop` example | R2DBC counterpart | Coverage decision |
|---|---|---|
| `01-ktor-application-architecture` | `02-ktor-production-integration` packages `app`, `config`, `routes`, `persistence` | Covered in the Ktor module instead of a standalone module |
| `02-spring-application-architecture` | `01-spring-production-integration` packages `app`, `persistence`, `web` | Covered in the Spring module instead of a standalone module |
| `03-spring-http-outbox-idempotency` | Spring `outbound` slice, `SpringOutboundDispatcher`, outbound tests | Covered by the Spring package slice |
| `04-ktor-http-outbox-idempotency` | Ktor `outbound` slice, `KtorOutboundDispatcher`, MockEngine tests | Covered by the Ktor package slice |
| `05-spring-auth-session` | Spring `auth` slice and session metadata tests | Covered by the Spring package slice |
| `06-ktor-auth-session` | Ktor authentication/session cookie flow and session metadata tests | Covered by the Ktor package slice |
| `07-spring-outbox-realtime` | Spring `realtime` SSE replay and persisted outbox tests | Covered by the Spring package slice |
| `08-ktor-outbox-realtime` | Ktor WebSocket replay/live stream and persisted outbox tests | Covered by the Ktor package slice |
| `09-spring-observability-readiness` | Spring diagnostics/readiness and request-correlation tests | Covered by the Spring package slice |
| `10-ktor-observability-readiness` | Ktor diagnostics/readiness and request-correlation tests | Covered by the Ktor package slice |
```

- [x] **Step 2: Add one sentence after the table**

Add:

```markdown
Future Chapter 12 R2DBC work should extend these package slices unless a new
issue proves that the module boundary itself is the teaching target.
```

## Task 3: Update Korean Chapter README

**Files:**
- Modify: `12-production-integration/README.ko.md`

- [x] **Step 1: Insert a Korean `Source Example Parity` section after `## 이슈별 주제 맵`**

Add the Korean equivalent table with the same ten source examples and the same
R2DBC counterpart paths.

- [x] **Step 2: Add the same future guardrail in Korean**

Required meaning:

```text
향후 Chapter 12 R2DBC 작업은 새 이슈가 module boundary 자체를 학습 목표로 증명하지 않는 한 기존 package slice를 확장한다.
```

## Task 4: Check Root README Wording

**Files:**
- Maybe modify: `README.md`
- Maybe modify: `README.ko.md`

- [x] **Step 1: Inspect Chapter 12 root rows**

Run:

```bash
rg -n 'Chapter 12 production integration|12-production-integration|#43' README.md README.ko.md
```

Expected:

- Root README already says Chapter 12 is covered by R2DBC issues `#43`-`#49`.

- [x] **Step 2: Edit only if the root wording is stale**

If edited, keep the change to one or two sentences that point readers to the
chapter README parity table. Do not duplicate the full ten-row table in the
root README.

## Task 5: Add Lesson Note

**Files:**
- Create: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md`

- [x] **Step 1: Create the lesson**

Content:

```markdown
# Issue #114 Chapter 12 Parity Refresh

## Context

Issue #114 revisited Chapter 12 after `exposed-workshop` added ten
topic-specific production-integration examples.

## Decision

Keep `exposed-r2dbc-workshop` Chapter 12 as two runtime modules:
`01-spring-production-integration` and `02-ktor-production-integration`.
The R2DBC teaching target is the Spring/Ktor runtime boundary plus
`suspendTransaction` repository slices, not one Gradle module per topic.

## Guardrail

Future Chapter 12 R2DBC work should extend package slices inside the two
existing modules unless a new issue proves that the module boundary itself is
the learning goal.

## Verification

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain`
```

## Task 6: Verify

**Files:**
- No planned edits.

- [x] **Step 1: Check markdown diff hygiene**

Run:

```bash
git diff --check
```

Expected: no trailing whitespace or conflict markers.

- [x] **Step 2: Verify Gradle project discovery**

Run:

```bash
./gradlew projects --console=plain
```

Expected: `:01-spring-production-integration` and `:02-ktor-production-integration` appear.

- [x] **Step 3: Verify targeted Chapter 12 tests**

Run:

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

Expected: both module test tasks pass.

## Task 7: Review And PR Prep

**Files:**
- Create: `docs/review/2026-07-05-issue-114-chapter12-parity-review.md`
- Create: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md` if Task 5 has not already done so.

- [x] **Step 1: Run 7-Tier local/native review**

Review scope:

- README parity claims map to current source/test paths.
- Spring Boot version wording maps to `gradle/libs.versions.toml`.
- No new module/workflow coverage is required because no module is added.
- P0/P1 must be 0 before PR creation.

- [ ] **Step 2: Commit with Lore protocol**

Use a commit message with intent line and trailers:

```text
Clarify Chapter 12 parity to prevent duplicate module splits

Constraint: Issue #114 is a parity refresh against exposed-workshop Chapter 12.
Rejected: Split R2DBC Chapter 12 into ten modules | existing design keeps one module per runtime stack.
Confidence: high
Scope-risk: narrow
Directive: Extend existing Chapter 12 package slices unless module boundaries become the explicit lesson.
Tested: git diff --check; ./gradlew projects; repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
Not-tested: Full repository build.
```

- [ ] **Step 3: Create PR**

PR metadata:

- Title: `docs: refresh chapter 12 R2DBC parity map`
- Body links `Fixes #114`.
- Assignee: `debop`.
- Labels copied from issue: `enhancement`, `examples`.
- Milestone: none.
- Final PR body section: `## DoD Status`.

## Self-Review

- Spec coverage: Tasks 1-7 cover all acceptance criteria from the design.
- Placeholder scan: no `TBD`, `TODO`, or implementation-later placeholder is used.
- Type/path consistency: all paths are current worktree paths or explicit source-workshop read-only paths.
- Code-bearing task status: no Kotlin code task is planned; if Task 1 finds a real code gap, add a new TDD task before Task 2.
