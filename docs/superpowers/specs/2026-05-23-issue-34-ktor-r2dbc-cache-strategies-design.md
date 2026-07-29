# Issue #34 Ktor R2DBC Cache Strategies 설계

## 목표

추가: a Ktor + Exposed R2DBC chapter 11 example that makes general cache strategy
behavior observable without entering the coroutine-specific cache topic reserved
for #69.

## 맥락

- `exposed-r2dbc-workshop` issue #34 mirrors `exposed-workshop` issue #47.
- Existing R2DBC cache behavior lives in
  `11-high-performance/02-cache-strategies-r2dbc`.
- Existing Ktor module patterns live in `10-multi-tenant/07-multitenant-ktor`
  and `12-production-integration/02-ktor-production-integration`.
- Baseline check passed:
  `repo-test-summary -- ./gradlew :02-cache-strategies-r2dbc:test -PuseDB=H2 --continue --console=plain`
  with 25 tests.

## 범위

- 추가: `11-high-performance/04-cache-strategies-ktor-r2dbc`.
- 사용: Ktor routes with Exposed R2DBC and a Redisson-backed cache-aside
  repository that mirrors the cache strategy behavior from the Spring WebFlux
  R2DBC module without depending on Spring abstractions.
- Show:
  - read-through/cache population,
  - cache hit versus miss visibility,
  - invalidation that preserves DB rows,
  - write-through/update behavior,
  - database fallback after cache clear.
- 추가: English and Korean README files.
- 추가: a committed PNG diagram under `docs/images/readme-diagrams/` plus the SVG
  source, matching the existing chapter 11 README diagram convention.
- Wire the module into chapter 11 docs and `Examples.yml`.

## 비목표

- 금지: implement cancellation-aware cache population; that belongs to #69.
- 금지: add routing datasource behavior; that belongs to #35.
- 금지: add a new shared cache abstraction unless duplication becomes unsafe.
- 금지: add Java code.

## 설계

### Module Boundary

The module is standalone and Ktor-owned:

- `KtorCacheStrategiesApplication.kt` installs plugins, initializes DB tables,
  and installs routes.
- `KtorCacheDatabase` owns the H2 R2DBC pool and Exposed database.
- `KtorRedissonFactory` creates the Redisson client for tests and local runs.
- `UserCacheRepository` owns a Redisson `RMap` plus Exposed R2DBC table
  operations.
- `UserRoutes` exposes cache-observable endpoints.

### Routes

| Method | Path | Behavior | Response |
|---|---|---|---|
| `GET` | `/users/{id}` | Read one user through cache with DB fallback | `200 UserCacheResponse`, or `404 UserCacheResponse(cacheStatus=NOT_FOUND)` |
| `GET` | `/users` | List seeded users directly from DB; list 읽는다: are not cached | `200 List<UserRecord>` |
| `POST` | `/users` | Insert a new user and populate cache | `201 UserCacheResponse(cacheStatus=WRITTEN)` |
| `PUT` | `/users/{id}` | Update an existing user and refresh cache | `200 UserCacheResponse(cacheStatus=WRITTEN)`, or `404 UserCacheResponse(cacheStatus=NOT_FOUND)` |
| `DELETE` | `/users/{id}/cache` | Invalidate one cache key only | `200 InvalidationResponse(invalidated=0|1)` |
| `DELETE` | `/users/cache` | Clear all cache keys for this module prefix | `200 InvalidationResponse(invalidated=n)` |
| `GET` | `/cache/stats` | Return simple hit/miss/update/invalidation counters | `200 CacheStatsRecord` |

The route response wraps data with `cacheStatus` so tests can assert observable
cache behavior instead of relying on timing.

User read response shape:

```json
{
  "cacheStatus": "MISS",
  "user": {
    "id": 1,
    "username": "alice",
    "firstName": "Alice",
    "lastName": "Cache"
  }
}
```

For `NOT_FOUND`, `user` is `null` and still present in the JSON object.

Allowed `cacheStatus` values are `HIT`, `MISS`, `NOT_FOUND`, and `WRITTEN`.
Invalidation responses use `InvalidationResponse`; `INVALIDATED` is a counter
key only and never appears as a `cacheStatus` value.

### Cache Observation

The repository exposes a single-pass read method:

```kotlin
sealed class CacheReadResult {
    data class Hit(val user: UserRecord): CacheReadResult()
    data class Miss(val user: UserRecord): CacheReadResult()
    data object NotFound: CacheReadResult()
}
```

`findCached(id)` performs one cache read, then an Exposed R2DBC DB fallback only
when that read returns no value. The returned sealed result is the source of
truth for route `cacheStatus` and counters. Tests do not infer hit/miss from
timing or a separate pre-check.

Redisson `RMap` calls are synchronous, so repository cache reads, writes,
invalidations, and clear operations run behind `withContext(Dispatchers.IO)`.
Ktor route handlers do not execute blocking Redis I/O on the caller coroutine
dispatcher.

The repository records local counters around cache operations:

- `MISS` when a key is absent before `get(id)` and the DB returns a row.
- `HIT` when a key is present before `get(id)`.
- `NOT_FOUND` when both cache and DB have no row.
- `WRITTEN` after `put(id, entity)` writes through the repository and refreshes
  the cache entry.
- `INVALIDATED` only as a stats counter after cache invalidation.

Counters are application-instance scoped and backed by `java.util.concurrent`
atomic counters. They are not stored in Redis. Each `testApplication` creates a
fresh module instance with a unique cache name such as
`exposed:ktor:r2dbc:users:test:<uuid>`, then clears that exact Redis key prefix
before and after the test.

Counters are example diagnostics, not production metrics.

### Data and DB

The module seeds deterministic user rows into H2 at startup. Tests use unique
database names and clear each test's unique Redis map name, so repeated test
JVMs do not share cache state. Redisson is owned by the application module and
closed on `ApplicationStopped`, matching the database pool lifecycle.

Cache entries have no TTL in this example; they live until explicit invalidation
or cache clear. The README documents this trade-off.

This module is H2-focused. The point is Ktor route/cache observability over the
same R2DBC + Redisson strategy family, not multi-database driver behavior. The
existing Spring cache strategy module remains the broader multi-DB cache
reference.

The diagram files are:

- `docs/images/readme-diagrams/11-high-performance-04-cache-strategies-ktor-r2dbc-architecture-01.svg`
- `docs/images/readme-diagrams/11-high-performance-04-cache-strategies-ktor-r2dbc-architecture-01.png`

## 수용 기준

- `:04-cache-strategies-ktor-r2dbc:test` passes.
- Tests cover cache population, cache hit, invalidation/update behavior, and DB
  fallback after cache clear.
- `Examples.yml` runs the new module for chapter 11 paths.
- README files explicitly say #69 owns coroutine-specific cache cancellation and
  concurrency behavior.
- Codex 6-Tier and Claude Code CLI 6-Tier reviews pass with P0=0 and P1=0.
