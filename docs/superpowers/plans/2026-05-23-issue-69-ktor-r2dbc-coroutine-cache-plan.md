# Issue #69 Ktor R2DBC Coroutine Cache 구현 계획

## Workflow

Type A Full Design. 이 module은 a new chapter 11 example with
coroutine/concurrency behavior, so use the full spec, plan, implementation,
verification, dual-review, lessons, PR, CI, merge, and qmd-sync sequence.

## 작업

1. Gate spec and plan.
   - 실행: Codex consistency review against #69, #34, and exposed-workshop #48.
   - 실행: Claude Code CLI advisor review.
   - Fix all P0/P1 findings and rerun until P0=0/P1=0.

2. 생성: module skeleton.
   - 추가: `11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`.
   - Reuse #34 Ktor/R2DBC/Redisson dependencies and resource layout.
   - 유지: package under `exposed.r2dbc.examples.cache.ktor.coroutines`.
   - 문서화: and implement close order in `KtorCoroutineCacheResources`:
     cancel and join loader scope, close Redisson, close R2DBC pool.
   - 검증: discovery with `./gradlew projects --console=plain`.

3. 구현: coroutine cache core.
   - 추가: `SingleFlightCacheLoader` with app-scope `Deferred` sharing.
   - 사용: `SupervisorJob`, explicit dispatcher, and `CoroutineName` for the
     loader scope.
   - Remove deferred entries on success, failure, or cancellation with
     `inFlight.remove(key, deferred)`.
   - 유지: the `computeIfAbsent` lambda non-blocking; create only the deferred
     there.
   - 추가: a five-second producer timeout and structured load-failure mapping.
   - Rethrow `CancellationException` from awaiting callers.
   - 금지: use `runCatching` around suspend paths.
   - 추가: English KDoc for public loader, repository, route response, and stats
     types.
   - 추가: pure tests for barrier-synchronized coalescing, failure cleanup,
     cancellation cleanup, owner-waiter cancellation with surviving coalesced
     waiters, and timeout-to-503 plus retry 다음 위치 뒤: timeout/failure.

4. 구현: repository and routes.
   - 추가: R2DBC table/record/request/response types.
   - 추가: repository cache read, DB fallback, create, update, invalidate, clear,
     and direct DB list operations.
   - Isolate Redisson sync operations with `withContext(Dispatchers.IO)`.
   - Parse dates and validate request strings before R2DBC transactions.
   - Limit `loadDelayMillis` to `0..1000`; 거부: out-of-range values with
     `400 INVALID_REQUEST`.
   - Treat cache write failure 다음 위치 뒤: DB fallback or DB commit as diagnostic:
     log it, record `cacheWriteFailures`, and return the DB row/status.
   - 추가: Ktor routes and status pages.

5. 추가: docs and diagram.
   - 추가: README.md and README.ko.md for the module.
   - 갱신: chapter 11 README.md and README.ko.md.
   - 추가: SVG source and PNG diagram under `docs/images/readme-diagrams/`.
   - State explicitly how #69 differs from #34.
   - Include a delta table against #34 covering backend, single-flight,
     cancellation, status taxonomy, stats, and lesson.
   - 문서화: demo-only constraints: no auth/rate limiting, no TTL/max-size
     policy, explicit invalidation only.

6. Wire CI.
   - 추가: module path to `Examples.yml` path filters.
   - 추가: `:05-cache-strategies-ktor-r2dbc-coroutines:test` to chapter 11 job.
   - 추가: test result/report artifact paths.
   - 실행: `actionlint`.

7. 검증: locally.
   - `./gradlew projects --console=plain`
   - `./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:compileKotlin :05-cache-strategies-ktor-r2dbc-coroutines:compileTestKotlin --warning-mode all --console=plain`
   - `repo-test-summary -- ./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
   - `repo-test-summary -- ./gradlew :02-cache-strategies-r2dbc:test :04-cache-strategies-ktor-r2dbc:test :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain`
   - `./gradlew detekt --parallel --console=plain`
   - `actionlint .github/workflows/Examples.yml`
   - `git diff --check`
   - Scan for forbidden coroutine patterns:
     `rg -n "runCatching|GlobalScope" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`
   - Scan `runBlocking` separately and verify any hit is a lifecycle
     startup/shutdown bridge, not a route or repository request path:
     `rg -n "runBlocking" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`
   - Scan catch ordering:
     `rg -n "catch \\(.*Exception" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines/src/main/kotlin`
     and verify every broad catch is preceded by a `CancellationException`
     rethrow path or is not on a suspend path.

8. 실행: code review gates.
   - Current Codex session 6-Tier review over the diff.
   - Claude Code CLI 6-Tier review over the same diff slice.
   - Fix all P0/P1 and rerun until P0=0/P1=0.

9. Lessons, commit, PR.
   - 추가: `docs/lessons/2026-05-23-issue-69-ktor-r2dbc-coroutine-cache.md`.
   - 커밋: with Lore protocol.
   - 푸시: and create PR with `Closes #69`.
   - 추가: post-PR dual-review evidence comment and attempt formal review.

10. CI, merge, sync.
    - Wait for required checks.
    - Rebase merge when green.
    - Remove feature worktree and branch.
    - Fast-forward local `develop`.
    - 실행: GitHub qmd sync, `qmd update`, and `QMD_LLAMA_GPU=false qmd embed`.
    - 검증: qmd sees issue #69 closed and the PR merged.

## 위험 제어

- 유지: Testcontainers-backed Gradle runs sequential.
- 금지: use `runBlocking` in production request paths.
- 금지: swallow cancellation in broad catch blocks.
- 금지: let cancelled or failed deferred values stay in the in-flight map.
- 유지: Redisson sync APIs behind `Dispatchers.IO`.
- 유지: #34 unchanged except docs/CI references needed to link the new module.
- 사용: bluetape4k assertion APIs in new tests; do not introduce AssertJ,
  Kluent, JUnit assertion APIs, or `kotlin.test` assertions.
