# Issue #204 PR 전 최종 리뷰

검토일: 2026-08-27
대상: `feat/issue-204-spring-boot-exposed-r2dbc` 전체 변경과 `develop` 기준 diff
범위: 새 `09-spring/06-exposed-spring-boot-r2dbc-repository` sibling, root/module README, workflow, diagram, plan과 lesson

## 독립 리뷰 게이트

| 리뷰 lane | 결과 | 근거 |
|---|---|---|
| `code-reviewer` | COMMENT (비차단 diagnostics 공백) | 재검토 46 unique paths; CRITICAL/HIGH/MEDIUM/LOW=0. `Serializable`/`serialVersionUID`와 당시 1.12.1 proxy wrapper 단언 반영 확인. LSP client 부재로 `APPROVE` 대신 비차단 COMMENT |
| `architect` | CLEAR | lifecycle ownership, provider 경계, plan traceability 재검토; P0/P1/P2=0 |
| security/plan review | PASS | validation, unknown-field, sanitizer, closed-pool, error disclosure; P0/P1/P2=0 |

## Six-lens 결과

| 관점 | 상태 | 증거와 판단 |
|---|---|---|
| Security | PASS | `ProductCreateRequest`는 ID를 받지 않고 unknown property를 거부하며 name/description 120/500자 제약을 둔다. generic `ProblemDetail`과 whitelist/type-only application log를 사용하고 credential·전체 URL을 기록하지 않는다. `ProductControllerTest`와 invalid configuration test가 no-overwrite·no-disclosure·실패를 고정한다. |
| Correctness/API | PASS | provider `table`/`extractId`/`toDomain`/`toPersistValues` mapping, nullable ID CRUD, `Flow` materialization, GET missing `404`, POST `201`, DELETE `204/404`를 source와 HTTP/repository test가 대응한다. |
| Performance/resource | PASS | `maxSize=1` temporary pool에서 5회 CRUD/stream cycle과 8개 bounded caller를 5초 window 안에 수행한다. recorder의 acquire/close/open 균형과 test-only timeout을 확인하며 throughput benchmark로 과장하지 않는다. |
| Coroutine/stability | PASS | stream cancellation에서 `CancellationException`을 재전파하고 후속 query가 성공한다. `ConnectionPoolLifecycle`은 Exposed manager unregister 후 pool dispose를 `AtomicBoolean`으로 idempotent하게 수행한다. context close/reopen, closed-pool 거부, invalid driver/URL, repeated initializer를 검증한다. |
| Developer/API maintainability | PASS | 모든 touched Kotlin test assertion은 `bluetape4k-assertions`로 통일했고 suspend 경로에서 broad `runCatching`으로 cancellation을 삼키지 않는다. public Kotlin API/KDoc, provider versionless alias, 05/06 sibling boundary가 source와 일치한다. |
| User/Ops/CI/docs | PASS | English/Korean README가 source-equivalent section/API 계약과 demo-only·metrics N/A 경계를 설명한다. locale SVG/PNG semantic·connector·arrowhead·endpoint·geometry·visual audit과 asset-pair audit이 통과했고, Examples `chapter-09` H2 job/path/artifact와 issue metadata가 정렬된다. |

## 이번 리뷰에서 닫은 finding

- touched test의 `kotlin.test`/JUnit assertion을 `bluetape4k-assertions`로 교체했다.
- `ProductRecord`와 `ProductCreateRequest`에 기존 workshop DTO 관례인 `Serializable`과
  `serialVersionUID`를 추가했다.
- 제약 위반 negative test의 `Throwable` 허용 범위를 당시 1.12.1 Spring suspend
  repository proxy가 노출하던 `UndeclaredThrowableException`으로 좁혔다. 이는
  해당 release의 historical contract이며, 2.0.0-SNAPSHOT provider direct proxy의
  target exception 재전파 계약으로 후속 migration되었다.
- `PerformanceStabilityTest`의 suspend acquire/delay/close에서 `CancellationException`을 명시적으로 재전파하도록 바꿨다.
- lifecycle/config KDoc에 `R2dbcDatabase` manager unregister → `ConnectionPool` dispose 순서를 명시했다.
- T6 plan의 cancellation/config 문구를 실제 후속 `count`, raw connection caller, source read-back 증거 범위로 축소했다. recorder를 app-bound service proxy에 잘못 연결하지 않으며 in-flight drain timing과 framework/driver 전체 redaction은 N/A로 남겼다.

## Fresh verification

| 검증 | 결과 |
|---|---|
| `./gradlew :06-exposed-spring-boot-r2dbc-repository:test --tests '*ProductR2dbcRepositoryTest' --tests '*ProductTransactionServiceTest' -PuseDB=H2 --no-daemon --console=plain` | `BUILD SUCCESSFUL`; 9 tests; 2.0.0-SNAPSHOT direct `IllegalArgumentException`과 partial commit/rollback 검증 |
| `./gradlew :06-exposed-spring-boot-r2dbc-repository:check -PuseDB=H2 --no-daemon --console=plain` | `BUILD SUCCESSFUL`; 28 tests; Kover verify 포함 |
| `repo-test-summary -- ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2 --no-daemon --console=plain` | exit 0; 28 tests; failure/error/skip 0 |
| `./gradlew test --rerun-tasks -PuseDB=H2 --no-daemon --console=plain` | `BUILD SUCCESSFUL`; fresh XML 187개/1,220 test cases, 1,072 executed·148 assumption skips; failure/error 0 |
| `git diff --check` | PASS |
| Kotlin LSP diagnostics | 실행 불가; 환경에 `kotlin-language-server` executable만 있고 LSP client가 없음. Kotlin compile/check와 test evidence로 보완 |
| README Korean terminology audit | findings=0 |
| workflow YAML/actionlint | PASS (workflow review artifact) |
| diagram semantic/XML/connector/arrowhead/endpoint/geometry/mixed-corner/visual/asset-pair | PASS; 전역 `--require-all-referenced`는 기존 공용 asset 119개 범위로 N/A |

## 최종 판정

현재 구현·문서·계획·검증에서 unresolved P0/P1/P2는 없다. 독립
`code-reviewer`의 LOW 두 건은 기존 DTO 관례와 당시 1.12.1 proxy 예외 타입으로
반영되어 재검토에서 0건으로 닫혔다. 이후 2.0.0-SNAPSHOT provider가 direct
`IllegalArgumentException`을 재전파하므로 consumer 테스트는 그 공개 계약을
따르도록 migration한다. LSP client 부재는 Kotlin compile/check와 fresh 테스트로
보완한 비차단 diagnostics 공백이다.

**현재 상태: PR-READY — code-reviewer COMMENT는 비차단 diagnostics 공백만 기록**
