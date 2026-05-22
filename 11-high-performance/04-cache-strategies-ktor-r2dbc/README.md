# 04-cache-strategies-ktor-r2dbc

[한국어](./README.ko.md)

Ktor + Exposed R2DBC cache strategy example for chapter 11. It mirrors the
general cache strategy topic from `02-cache-strategies-r2dbc`, but exposes the
cache behavior through Ktor routes instead of Spring WebFlux controllers.

## Architecture

![Ktor R2DBC cache strategy flow](../../docs/images/readme-diagrams/11-high-performance-04-cache-strategies-ktor-r2dbc-architecture-01.png)

## What This Module Shows

| Area | Decision |
|---|---|
| Cache backend | Redisson `RMap` with an explicit cache name |
| DB access | Exposed R2DBC transactions over an H2 R2DBC pool |
| Read path | `GET /users/{id}` returns `HIT`, `MISS`, or `NOT_FOUND` |
| Write path | `POST /users` and `PUT /users/{id}` write DB and refresh cache |
| Invalidation | Cache key or full-module cache clear; DB rows remain |
| Stats | Application-local counters, not Redis- or Micrometer-backed production metrics |

List reads (`GET /users`) are DB-direct so the single-row cache behavior stays
visible. Cache entries have no TTL in this example; they live until explicit
invalidation or cache clear. JSON request bodies are strict: unknown fields are
rejected so the workshop contract stays explicit.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/users` | List seeded users directly from DB |
| `GET` | `/users/{id}` | Read one user through cache with DB fallback |
| `POST` | `/users` | Create one user and populate cache |
| `PUT` | `/users/{id}` | Update one user and refresh cache |
| `DELETE` | `/users/{id}/cache` | Invalidate one cache key |
| `DELETE` | `/users/cache` | Clear all keys for this module cache name |
| `GET` | `/cache/stats` | Read local hit/miss/write/invalidation counters |

## Run Tests

```bash
repo-test-summary -- ./gradlew :04-cache-strategies-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain
```

The tests cover first-read population, second-read hit, invalidation with DB
fallback, create/update cache refresh, clear with DB fallback, DB-direct list
reads, and structured error responses.

## Relationship to #69

This module intentionally does not demonstrate cancellation-safe cache
population, single-flight loading, compare-and-set cache refresh, or concurrent
suspend-call behavior. Those coroutine-specific cache concerns belong to #69.
