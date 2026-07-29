# Issue #114 Chapter 12 R2DBC Parity 구현 계획

> **작업자 참고:** 이 계획은 task 단위로 구현하며, 권장 실행 표면은 superpowers:subagent-driven-development이고 대안은 superpowers:executing-plans다. 진행 상태는 checkbox(`- [ ]`)로 추적한다.

**목표:** 현재 10개의 `exposed-workshop` Chapter 12 examples 명시적으로 추적 가능하게 한다: the two existing R2DBC Chapter 12 modules.

**아키텍처:** 기존 two-module R2DBC design. 명시적인 README parity mapping and a lesson note; production/test code는 다음 경우에만 수정한다: the parity audit finds a real missing behavior.

**기술 스택:** Kotlin 2.4.0 and Spring Boot 4.1.0 from `gradle/libs.versions.toml`, Ktor 3, Exposed R2DBC, Gradle, bluetape4k assertions/test infrastructure.

---

## 파일 구조

- 수정: `12-production-integration/README.md`
  - 책임: English Chapter 12 concept and source-example parity map.
- 수정: `12-production-integration/README.ko.md`
  - 책임: Korean Chapter 12 concept and source-example parity map.
- 생성: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md`
  - 책임: durable lesson for future Chapter 12 parity triage.
- 필요 시 수정: `README.md`, `README.ko.md`
  - 책임: root parity wording only if the Chapter 12 row is stale 다음 위치 뒤: the chapter README update.

다음 경우가 아니면 Kotlin source file 수정은 계획하지 않는다: 작업 2에서 table 항목을 발견하는 경우
entry with no current source/test counterpart.

## 작업 1: 현재 증거 고정

**파일:**
- 읽기: `12-production-integration/README.md`
- 읽기: `12-production-integration/README.ko.md`
- 읽기: `12-production-integration/01-spring-production-integration/src/test/kotlin/exposed/r2dbc/examples/production/spring/SpringProductionIntegrationApplicationTest.kt`
- 읽기: `12-production-integration/02-ktor-production-integration/src/test/kotlin/exposed/r2dbc/examples/production/ktor/KtorProductionIntegrationApplicationTest.kt`
- 읽기: `/Users/debop/work/bluetape4k/exposed-workshop/12-production-integration/README.md`

- [x] **단계 1: Re-run the source/current mapping commands**

실행:

```bash
find /Users/debop/work/bluetape4k/exposed-workshop/12-production-integration -maxdepth 2 -type d | sort
find 12-production-integration -maxdepth 2 -type d | sort
rg -n 'application architecture|HTTP Client Outbox|Authentication And Sessions|Realtime Outbox|Observability And Readiness' 12-production-integration/README.md 12-production-integration/README.ko.md
rg -n 'class .*Test|fun `' 12-production-integration/01-spring-production-integration/src/test/kotlin 12-production-integration/02-ktor-production-integration/src/test/kotlin
rg -n 'kotlin = |spring-boot = ' gradle/libs.versions.toml
```

기대:

- source workshop은 다음을 나열한다: `01-*` through `10-*`.
- R2DBC workshop은 다음만 나열한다: `01-spring-production-integration` and `02-ktor-production-integration`.
- Chapter README pair는 이미 다음을 명명한다: the five production topics.
- tests는 다음을 포함한다: auth/session, realtime, outbound/idempotency, diagnostics/readiness, and request-id cases.
- `gradle/libs.versions.toml` 명세와 계획에 적힌 Kotlin 및 Spring Boot line을 확인한다.

- [x] **단계 2: Record any actual gap before editing docs**

source topic에 현재 R2DBC counterpart가 없으면 중단하고 작업 3 전에 좁은 code/test 작업을 추가한다. 그렇지 않으면 docs-only 구현을 계속한다.

## 작업 2: 영어 Chapter README 갱신

**파일:**
- 수정: `12-production-integration/README.md`

- [x] **단계 1: 다음을 삽입한다: `Source Example Parity` section 다음 위치 뒤: `## Topic Map`**

추가: this table:

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

- [x] **단계 2: 추가: one sentence 다음 위치 뒤: the table**

추가:

```markdown
Future Chapter 12 R2DBC work should extend these package slices unless a new
issue proves that the module boundary itself is the teaching target.
```

## 작업 3: 한국어 Chapter README 갱신

**파일:**
- 수정: `12-production-integration/README.ko.md`

- [x] **단계 1: 다음을 삽입한다: Korean `Source Example Parity` section 다음 위치 뒤: `## 이슈별 주제 맵`**

추가: the Korean equivalent table with the same ten source examples and the same
R2DBC counterpart paths.

- [x] **단계 2: 추가: the same future guardrail in Korean**

필수 의미:

```text
향후 Chapter 12 R2DBC 작업은 새 이슈가 module boundary 자체를 학습 목표로 증명하지 않는 한 기존 package slice를 확장한다.
```

## 작업 4: 루트 README 문구 확인

**파일:**
- 필요 시 수정: `README.md`
- 필요 시 수정: `README.ko.md`

- [x] **단계 1: Inspect Chapter 12 root rows**

실행:

```bash
rg -n 'Chapter 12 production integration|12-production-integration|#43' README.md README.ko.md
```

기대:

- Root README already says Chapter 12 is covered by R2DBC issues `#43`-`#49`.

- [x] **단계 2: Edit only if the root wording is stale**

If edited, keep the change to one or two sentences that point readers to the
chapter README parity table. 금지: duplicate the full ten-row table in the
root README.

## 작업 5: Lesson Note 추가

**파일:**
- 생성: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md`

- [x] **단계 1: 생성: the lesson**

내용:

```markdown
# Issue #114 Chapter 12 Parity Refresh

## Context

Issue #114 revisited Chapter 12 다음 위치 뒤: `exposed-workshop` added ten
topic-specific production-integration examples.

## Decision

유지: `exposed-r2dbc-workshop` Chapter 12 as two runtime modules:
`01-spring-production-integration` and `02-ktor-production-integration`.
The R2DBC teaching target is the Spring/Ktor runtime boundary plus
`suspendTransaction` repository slices, not one Gradle module per topic.

## Guardrail

Future Chapter 12 R2DBC work should extend package slices inside the two
existing modules unless a new issue proves that the module boundary itself is
the learning goal.

## 검증

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain`
```

## 작업 6: 검증

**파일:**
- 계획된 수정 없음.

- [x] **단계 1: Check markdown diff hygiene**

실행:

```bash
git diff --check
```

기대: no trailing whitespace or conflict markers.

- [x] **단계 2: 검증: Gradle project discovery**

실행:

```bash
./gradlew projects --console=plain
```

기대: `:01-spring-production-integration` and `:02-ktor-production-integration` appear.

- [x] **단계 3: 검증: targeted Chapter 12 tests**

실행:

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

기대: both module test tasks pass.

## 작업 7: 검토와 PR 준비

**파일:**
- 생성: `docs/review/2026-07-05-issue-114-chapter12-parity-review.md`
- 생성: `docs/lessons/2026-07-05-issue-114-chapter12-parity.md` 작업 5에서 아직 생성하지 않은 경우.

- [x] **단계 1: 실행: 7-Tier local/native review**

검토 범위:

- README parity claims map to current source/test paths.
- Spring Boot version wording maps to `gradle/libs.versions.toml`.
- 새 module을 추가하지 않으므로 새 module/workflow coverage는 필요하지 않다.
- P0/P1 must be 0 before PR creation.

- [ ] **단계 2: 커밋: with Lore protocol**

사용: a commit message with intent line and trailers:

```text
Clarify Chapter 12 parity to prevent duplicate module splits

Constraint: Issue #114 is a parity refresh against exposed-workshop Chapter 12.
Rejected: Split R2DBC Chapter 12 into ten modules | existing design keeps one module per runtime stack.
Confidence: high
Scope-risk: narrow
Directive: 확장: existing Chapter 12 package slices unless module boundaries become the explicit lesson.
Tested: git diff --check; ./gradlew projects; repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
Not-tested: Full repository build.
```

- [ ] **단계 3: 생성: PR**

PR metadata:

- Title: `docs: refresh chapter 12 R2DBC parity map`
- Body links `Fixes #114`.
- Assignee: `debop`.
- Labels copied from issue: `enhancement`, `examples`.
- Milestone: none.
- Final PR body section: `## DoD Status`.

## 자체 검토

- 명세 coverage: Tasks 1-7 cover all acceptance criteria from the design.
- Placeholder scan: no `TBD`, `TODO`, or implementation-later placeholder is used.
- Type/path consistency: all paths are current worktree paths or explicit source-workshop read-only paths.
- 코드 포함 작업 상태: no Kotlin code task는 계획하지 않는다; 작업 1에서 실제 code gap을 발견하면 작업 2 전에 새 TDD 작업을 추가한다.
