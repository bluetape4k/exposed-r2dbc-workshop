# Issue #69 Ktor R2DBC Coroutine Cache Plan

## Workflow

Type A Full Design. The module introduces a new chapter 11 example with
coroutine/concurrency behavior, so use the full spec, plan, implementation,
verification, dual-review, lessons, PR, CI, merge, and qmd-sync sequence.

## Tasks

1. Gate spec and plan.
   - Run Codex consistency review against #69, #34, and exposed-workshop #48.
   - Run Claude Code CLI advisor review.
   - Fix all P0/P1 findings and rerun until P0=0/P1=0.

2. Create module skeleton.
   - Add `11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`.
   - Reuse #34 Ktor/R2DBC/Redisson dependencies and resource layout.
   - Keep package under `exposed.r2dbc.examples.cache.ktor.coroutines`.
   - Document and implement close order in `KtorCoroutineCacheResources`:
     cancel and join loader scope, close Redisson, close R2DBC pool.
   - Verify discovery with `./gradlew projects --console=plain`.

3. Implement coroutine cache core.
   - Add `SingleFlightCacheLoader` with app-scope `Deferred` sharing.
   - Use `SupervisorJob`, explicit dispatcher, and `CoroutineName` for the
     loader scope.
   - Remove deferred entries on success, failure, or cancellation with
     `inFlight.remove(key, deferred)`.
   - Keep the `computeIfAbsent` lambda non-blocking; create only the deferred
     there.
   - Add a five-second producer timeout and structured load-failure mapping.
   - Rethrow `CancellationException` from awaiting callers.
   - Do not use `runCatching` around suspend paths.
   - Add English KDoc for public loader, repository, route response, and stats
     types.
   - Add pure tests for barrier-synchronized coalescing, failure cleanup,
     cancellation cleanup, owner-waiter cancellation with surviving coalesced
     waiters, and timeout-to-503 plus retry after timeout/failure.

4. Implement repository and routes.
   - Add R2DBC table/record/request/response types.
   - Add repository cache read, DB fallback, create, update, invalidate, clear,
     and direct DB list operations.
   - Isolate Redisson sync operations with `withContext(Dispatchers.IO)`.
   - Parse dates and validate request strings before R2DBC transactions.
   - Limit `loadDelayMillis` to `0..1000`; reject out-of-range values with
     `400 INVALID_REQUEST`.
   - Treat cache write failure after DB fallback or DB commit as diagnostic:
     log it, record `cacheWriteFailures`, and return the DB row/status.
   - Add Ktor routes and status pages.

5. Add docs and diagram.
   - Add README.md and README.ko.md for the module.
   - Update chapter 11 README.md and README.ko.md.
   - Add SVG source and PNG diagram under `docs/images/readme-diagrams/`.
   - State explicitly how #69 differs from #34.
   - Include a delta table against #34 covering backend, single-flight,
     cancellation, status taxonomy, stats, and lesson.
   - Document demo-only constraints: no auth/rate limiting, no TTL/max-size
     policy, explicit invalidation only.

6. Wire CI.
   - Add module path to `Examples.yml` path filters.
   - Add `:05-cache-strategies-ktor-r2dbc-coroutines:test` to chapter 11 job.
   - Add test result/report artifact paths.
   - Run `actionlint`.

7. Verify locally.
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

8. Run code review gates.
   - Current Codex session 6-Tier review over the diff.
   - Claude Code CLI 6-Tier review over the same diff slice.
   - Fix all P0/P1 and rerun until P0=0/P1=0.

9. Lessons, commit, PR.
   - Add `docs/lessons/2026-05-23-issue-69-ktor-r2dbc-coroutine-cache.md`.
   - Commit with Lore protocol.
   - Push and create PR with `Closes #69`.
   - Add post-PR dual-review evidence comment and attempt formal review.

10. CI, merge, sync.
    - Wait for required checks.
    - Rebase merge when green.
    - Remove feature worktree and branch.
    - Fast-forward local `develop`.
    - Run GitHub qmd sync, `qmd update`, and `QMD_LLAMA_GPU=false qmd embed`.
    - Verify qmd sees issue #69 closed and the PR merged.

## Risk Controls

- Keep Testcontainers-backed Gradle runs sequential.
- Do not use `runBlocking` in production request paths.
- Do not swallow cancellation in broad catch blocks.
- Do not let cancelled or failed deferred values stay in the in-flight map.
- Keep Redisson sync APIs behind `Dispatchers.IO`.
- Keep #34 unchanged except docs/CI references needed to link the new module.
- Use bluetape4k assertion APIs in new tests; do not introduce AssertJ,
  Kluent, JUnit assertion APIs, or `kotlin.test` assertions.
