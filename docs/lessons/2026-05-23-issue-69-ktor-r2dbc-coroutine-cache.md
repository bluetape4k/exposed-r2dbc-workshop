# Issue 69 - Ktor R2DBC Coroutine Cache

## Context

Issue #69 adds a Ktor-specific coroutine cache example for chapter 11. It is
separate from the general Ktor cache strategy module from issue #34 because this
module focuses on suspend route handlers, caller cancellation, app-scoped
single-flight loading, and timeout-visible database fallback.

## Decisions

- Use an application-owned `SupervisorJob + Dispatchers.IO` loader scope so
  caller cancellation does not cancel the shared producer for other waiters.
- Keep one transient in-flight `Deferred` per key for overlapping cold misses
  and remove it with `inFlight.remove(key, deferred)` to avoid stale removal
  races.
- Convert producer timeout to a structured `503 CACHE_LOAD_TIMEOUT` response
  and verify the same key can retry successfully after timeout.
- Treat cache write failures as diagnostic-only after the database row is
  available: log, increment `cacheWriteFailures`, and return the DB value.
- Keep Redisson synchronous map access behind `Dispatchers.IO`.
- Document demo limitations explicitly: no auth/rate limiting/TTL/size limit,
  per-process stats only, transient in-flight growth, and fail-fast startup
  initialization without retry.

## Verification

- `./gradlew projects :05-cache-strategies-ktor-r2dbc-coroutines:compileKotlin :05-cache-strategies-ktor-r2dbc-coroutines:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- `repo-test-summary -- ./gradlew :02-cache-strategies-r2dbc:test :04-cache-strategies-ktor-r2dbc:test :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- `rg -n "runCatching|GlobalScope" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`
- `rg -n "runBlocking" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`
- `rg -n "catch \\(.*Exception" 11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines/src/main/kotlin`

Codex 6-Tier review and Claude Code CLI 6-Tier review both passed with
P0 = 0 and P1 = 0. Claude's P2/P3 review notes were resolved or documented
before PR creation.
