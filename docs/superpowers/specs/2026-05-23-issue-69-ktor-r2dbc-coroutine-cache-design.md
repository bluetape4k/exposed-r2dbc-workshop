# Issue #69 Ktor R2DBC Coroutine Cache Design

## Goal

Add a chapter 11 Ktor + Exposed R2DBC example focused on coroutine-specific
cache behavior: concurrent suspend callers, cancellation-safe cache population,
single-flight database fallback, and explicit invalidation/update behavior.

## Context

- `exposed-r2dbc-workshop` issue #69 mirrors `exposed-workshop` issue #48.
- #69 was split from #34 so the general cache strategy module can stay focused
  on route-visible hit/miss behavior.
- Existing #34 module:
  `11-high-performance/04-cache-strategies-ktor-r2dbc`.
- Existing Spring/WebFlux R2DBC cache reference:
  `11-high-performance/02-cache-strategies-r2dbc`.
- Existing JDBC coroutine cache reference in `exposed-workshop`:
  `11-high-performance/02-cache-strategies-coroutines`.

## Scope

- Add `11-high-performance/05-cache-strategies-ktor-r2dbc-coroutines`.
- Use Ktor suspend route handlers and Exposed R2DBC transactions.
- Use Redisson `RMap` as the distributed cache backend, with blocking Redisson
  operations isolated behind `withContext(Dispatchers.IO)`.
- Add a small app-lifecycle-bound single-flight loader so concurrent cache
  misses for the same key share one DB fallback.
- Keep request cancellation explicit:
  - cancelled request waiters rethrow `CancellationException`,
  - failed/cancelled in-flight deferred values are removed,
  - application shutdown cancels the loader scope,
  - cancellation does not poison later cache reads.
- Add English and Korean README files that explain how this differs from #34.
- Add a committed PNG diagram under `docs/images/readme-diagrams/` plus its SVG
  source.
- Wire chapter 11 docs and Examples CI paths/job/artifacts.

## Non-Goals

- Do not reimplement the broad cache strategy comparison already covered by
  `02-cache-strategies-r2dbc`.
- Do not duplicate #34's stats-only general cache module under a new name.
- Do not add routing datasource behavior; that belongs to #35.
- Do not add Java code.
- Do not introduce a reusable production cache framework. This is a workshop
  module that demonstrates one bounded coroutine pattern.

## Design

### Module Boundary

Package root:

```text
exposed.r2dbc.examples.cache.ktor.coroutines
```

Primary files:

| File | Responsibility |
|---|---|
| `KtorCoroutineCacheApplication.kt` | Ktor module entrypoint |
| `KtorCoroutineCacheResources.kt` | DB pool, Redisson, repository, and loader scope lifecycle |
| `SingleFlightCacheLoader.kt` | Per-key in-flight deferred sharing and cleanup |
| `CoroutineUserCacheRepository.kt` | Redisson + Exposed R2DBC cache-aside operations |
| `CoroutineUserRoutes.kt` | Route handlers and status mapping |
| `CoroutineCacheObservation.kt` | AtomicFU local counters |

Resource close order is fixed: cancel the loader scope and wait for children to
finish or cancel, then close Redisson, then close the R2DBC pool. The loader
must not keep running against already-closed Redis or database resources.

### Routes

The module is standalone, so it can use the same simple route shape as #34.

| Method | Path | Behavior | Response |
|---|---|---|---|
| `GET` | `/users/{id}` | Cache read with single-flight DB fallback | `200 CoroutineUserCacheResponse`, or `404` |
| `GET` | `/users` | Direct DB list; does not affect cache counters | `200 List<UserRecord>` |
| `POST` | `/users` | Insert DB row and populate cache | `201 CoroutineUserCacheResponse(cacheStatus=WRITTEN)` |
| `PUT` | `/users/{id}` | Update DB row and refresh cache | `200 CoroutineUserCacheResponse(cacheStatus=WRITTEN)`, or `404` |
| `DELETE` | `/users/{id}/cache` | Invalidate one cache key | `200 InvalidationResponse` |
| `DELETE` | `/users/cache` | Clear all keys for the module cache name | `200 InvalidationResponse` |
| `GET` | `/cache/stats` | Read local coroutine/cache counters | `200 CoroutineCacheStatsRecord` |

`GET /users/{id}` accepts optional `loadDelayMillis` for deterministic workshop
tests. The delay happens inside the single-flight loader before DB fallback.
The accepted range is `0..1000` milliseconds. Values outside that range return
`400 INVALID_REQUEST`; they are not clamped.

Stats counters are per-process workshop diagnostics. They are not stored in
Redis, not aggregated across Ktor instances, and not a Micrometer replacement.

### Response Statuses

`cacheStatus` values:

| Status | Meaning |
|---|---|
| `HIT` | Value came from Redisson cache |
| `MISS` | This caller created the DB fallback and populated cache |
| `COALESCED` | This caller shared an already-running DB fallback |
| `NOT_FOUND` | Cache and DB had no row |
| `WRITTEN` | Create/update wrote DB and refreshed cache |

Cancellation is not a normal HTTP response status in this module. The cancelled
coroutine rethrows cancellation and the stats endpoint exposes cancellation
counts from repository-level tests.

### Single-Flight Loading

`SingleFlightCacheLoader<K, V>` owns a `ConcurrentHashMap<K, Deferred<V?>>`.
Calls for the same key use `computeIfAbsent`:

1. The first caller creates an app-scope `async` producer.
2. Later callers await the same producer and receive `COALESCED`.
3. `invokeOnCompletion` removes the deferred for success, failure, or
   cancellation with `inFlight.remove(key, deferred)`. One-argument removal is
   forbidden because it can remove a newer deferred installed by a retry.
4. Awaiting callers catch `CancellationException`, record cancellation, and
   rethrow it.

The producer scope is application-owned rather than request-owned on purpose:
one cancelled request must not cancel the shared DB fallback for other waiters.
The scope is still bounded by Ktor application lifecycle and is cancelled during
resource close. The scope must use `SupervisorJob` plus an explicit dispatcher
and a `CoroutineName`, for example
`CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("ktor-coroutine-cache-loader"))`.
One producer failure must not cancel the loader scope or block later loads.

The `computeIfAbsent` lambda must only create the `Deferred`; Redisson and DB
I/O must happen inside the deferred body, never inside the map factory lambda.

Each producer wraps DB fallback with a five-second timeout. Timeout removes the
in-flight deferred, records a load failure, and surfaces as a structured `503`
from the route layer. Callers may still have shorter request/test timeouts.

### Repository Behavior

`CoroutineUserCacheRepository.findCached(id, loadDelayMillis)`:

1. Validate positive id with bluetape4k validation helpers.
2. Read Redisson on `Dispatchers.IO`.
3. Return `HIT` if present.
4. Otherwise call the single-flight loader.
5. The loader optionally delays, reads from Exposed R2DBC inside
   `suspendTransaction`, writes the loaded row into Redisson on
   `Dispatchers.IO`, and returns the row.
6. Map loader ownership to `MISS` or `COALESCED`.

Create and update parse/validate request data before opening the R2DBC
transaction. Missing update rows return `NOT_FOUND`, not a generic error.

Cache write failures after a successful DB fallback are diagnostic failures, not
read failures. The repository logs the cache write failure, records
`cacheWriteFailures`, and still returns the DB row as `MISS` or `COALESCED`.
Explicit create/update operations use the same rule after the DB commit:
the response remains `WRITTEN`, and cache refresh failure is visible through
stats and logs.

Catch ordering is part of the contract: `CancellationException` must be caught
and rethrown before any broad exception handling. Do not wrap suspend paths in
`runCatching`; it can hide coroutine cancellation and violates the module goal.

Cache entries have no TTL in this example. The README pair must call out that
the module is a demo-only bounded workshop surface, has no auth/rate limiting,
and uses explicit invalidation/cache clear rather than production eviction.
The README pair must also mention that the in-flight map can grow transiently
with the number of distinct concurrent cold-key loads, but each entry is bounded
by completion, failure, cancellation, or the five-second producer timeout.

The README pair must include a delta table against #34 with at least these
rows: cache backend, single-flight behavior, cancellation behavior,
`cacheStatus` taxonomy, stats counters, and intended lesson.

### Tests

Targeted tests must cover:

- first read is `MISS`, second read is `HIT`,
- concurrent reads for one cold key produce one `MISS` and the rest
  `COALESCED`,
- cancellation of the producer-owner request waiter is rethrown while other
  coalesced waiters still receive the loaded value,
- cancellation of one waiter does not poison later reads,
- loader failure removes in-flight state so retry works,
- Redisson cache write failure still returns the DB row and records
  `cacheWriteFailures`,
- producer timeout returns structured `503`, removes the in-flight entry, and a
  later retry can succeed,
- create/update refresh cache,
- invalidation and cache clear keep DB fallback working,
- list reads bypass cache counters,
- invalid ids and invalid dates return structured `400`.

Use `runSuspendIO` for real Ktor/Testcontainers/R2DBC I/O. Use
`kotlinx.coroutines.test.runTest` only for pure single-flight unit tests without
real I/O. Coalescing tests must use an explicit synchronization barrier such as
`CompletableDeferred` or `CountDownLatch`; a sleep-only timing assertion is not
sufficient.

## Acceptance Criteria

- `:05-cache-strategies-ktor-r2dbc-coroutines:test` passes.
- `./gradlew projects --console=plain` discovers the module.
- Chapter 11 README pair and module README pair are updated.
- Diagram PNG and SVG are committed under `docs/images/readme-diagrams/`.
- `Examples.yml` runs and uploads the new module's test results.
- `actionlint .github/workflows/Examples.yml` passes.
- Codex 6-Tier and Claude Code CLI 6-Tier reviews pass with P0=0 and P1=0.
