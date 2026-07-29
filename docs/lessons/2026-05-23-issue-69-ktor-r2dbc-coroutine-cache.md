# Issue 69 Ktor R2DBC Coroutine Cache 교훈

## 맥락

Issue #69는 chapter 11에 Ktor-specific coroutine cache example을 추가한다. 이 module은 issue #34의 general Ktor cache strategy module과 분리된다. 초점이 suspend route handler, caller cancellation, app-scoped single-flight loading, timeout-visible database fallback에 있기 때문이다.

## 결정

- Application-owned `SupervisorJob + Dispatchers.IO` loader scope를 사용해 caller cancellation이 다른 waiter가 공유하는 producer를 취소하지 않게 한다.
- Overlapping cold miss에는 key마다 transient in-flight `Deferred`를 하나 유지하고, stale removal race를 피하기 위해 `inFlight.remove(key, deferred)`로 제거한다.
- Producer timeout은 structured `503 CACHE_LOAD_TIMEOUT` response로 변환하고, 같은 key가 timeout 뒤 성공적으로 retry될 수 있음을 검증한다.
- Database row를 사용할 수 있게 된 뒤 cache write failure는 diagnostic-only로 다룬다. Log를 남기고 `cacheWriteFailures`를 증가시킨 뒤 DB value를 반환한다.
- Redisson synchronous map access는 `Dispatchers.IO` 뒤에 둔다.
- Demo limitation을 명시적으로 문서화한다: auth/rate limiting/TTL/size limit 없음, per-process stats only, transient in-flight growth, retry 없는 fail-fast startup initialization.

## 검증

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

Codex 6-Tier review와 Claude Code CLI 6-Tier review는 모두 `P0 = 0`, `P1 = 0`으로 통과했다. Claude의 P2/P3 review note는 PR 생성 전에 해결하거나 문서화했다.
