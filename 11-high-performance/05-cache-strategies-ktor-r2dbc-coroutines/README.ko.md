# 05-cache-strategies-ktor-r2dbc-coroutines

[English](./README.md)

Chapter 11의 Ktor + Exposed R2DBC 코루틴 캐시 예제입니다. 이 모듈은
동시 suspend 호출, 요청 취소, single-flight DB fallback을 다룹니다.
일반적인 cache hit/miss 관찰은 `04-cache-strategies-ktor-r2dbc`에 두고,
코루틴 전용 동작만 이 모듈에서 분리해 설명합니다.

## Architecture

![Ktor R2DBC coroutine cache flow](../../docs/images/readme-diagrams/11-high-performance-05-cache-strategies-ktor-r2dbc-coroutines-architecture-01.png)

## 이 모듈에서 확인하는 것

| 영역 | 결정 |
|---|---|
| Cache backend | Redisson `RMap`, blocking Redis 호출은 `Dispatchers.IO`로 격리 |
| DB access | H2 R2DBC pool 위의 Exposed R2DBC transaction |
| Cold read | 하나의 producer만 DB fallback 수행, 동시 호출은 `COALESCED` 반환 |
| Cancellation | 취소된 waiter는 cancellation을 다시 던지고 shared producer를 오염시키지 않음 |
| Timeout | Producer timeout은 `503 CACHE_LOAD_TIMEOUT`으로 응답하고 in-flight 상태 제거 |
| Diagnostics | 프로세스 로컬 AtomicFU counter; Redis/cluster/Micrometer metric 아님 |

## `04-cache-strategies-ktor-r2dbc`와 차이

| 항목 | `04-cache-strategies-ktor-r2dbc` | 이 모듈 |
|---|---|---|
| 학습 목표 | 일반 cache hit/miss, invalidation, DB fallback | Coroutine single-flight, cancellation, timeout, 동시 suspend 호출 |
| Cache backend | Redisson `RMap` | Redisson `RMap` |
| Single-flight | 없음 | key별 in-flight `Deferred` 하나 공유 |
| Cancellation | 핵심 주제 아님 | waiter 취소는 다시 던지고 실패/취소된 deferred는 제거 |
| Read status | `HIT`, `MISS`, `NOT_FOUND`, `WRITTEN` | `COALESCED` 추가 |
| Stats | hit/miss/write/invalidation 로컬 counter | coalesced, cancellation, load failure, cache write failure counter 추가 |

## Endpoints

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/users` | Seed user를 DB에서 직접 조회 |
| `GET` | `/users/{id}` | Cache + single-flight DB fallback으로 user 조회 |
| `POST` | `/users` | User 생성 후 cache populate |
| `PUT` | `/users/{id}` | User 수정 후 cache refresh |
| `DELETE` | `/users/{id}/cache` | Cache key 하나 무효화 |
| `DELETE` | `/users/cache` | 이 모듈 cache name의 모든 key clear |
| `GET` | `/cache/stats` | 로컬 coroutine/cache counter 조회 |

`GET /users/{id}`는 deterministic workshop test를 위해
`loadDelayMillis=0..1000`을 받습니다. 범위를 벗어나면
`400 INVALID_REQUEST`를 반환합니다.

## 제한사항

이 모듈은 demo-only workshop 예제입니다. Auth, rate limiting, TTL, 최대 cache
size, 분산 metric aggregation을 제공하지 않습니다. In-flight map은 동시에
들어온 서로 다른 cold key 수만큼 일시적으로 커질 수 있지만, 성공, 실패,
취소, 5초 producer timeout 시 entry가 제거됩니다.
Startup data initialization은 fail-fast이며 retry하지 않습니다. 운영 서비스는
이 단계에 retry/backoff 또는 readiness orchestration을 추가해야 합니다.

## 테스트 실행

```bash
repo-test-summary -- ./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:test -PuseDB=H2 --continue --console=plain
```

테스트는 hit/miss, 동시 read coalescing, waiter cancellation, producer timeout,
cache write failure diagnostic, invalidation, write refresh, DB-direct list read,
structured error를 검증합니다.
