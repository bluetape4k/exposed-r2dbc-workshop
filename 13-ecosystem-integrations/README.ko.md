# 13장: Ecosystem Integrations

[English](README.md) | [한국어](README.ko.md)

13장은 실제 R2DBC workshop 예제로 설명할 수 있는 ecosystem integration만
남깁니다. CockroachDB retry handling, Ktor integration, custom Spring Modulith
publication log, DDD boundary 예제가 같은 coroutine-first database boundary를
공유합니다. BigQuery, Trino, StarRocks, DuckDB는 실무 workshop 경로가 JDBC,
HTTP/native client, embedded client 중심이므로 여기서는 R2DBC 예제로 포팅하지
않습니다.

## 모듈

| 모듈 | 시나리오 | 기본 실행 |
|---|---|---|
| [`03-cockroachdb-retry`](03-cockroachdb-retry/) | CockroachDB retryable SQLSTATE `40001`에서 inventory reservation transaction을 재실행 | H2 R2DBC transaction replay와 synthetic SQLSTATE failure |
| [`05-ktor-exposed-integration`](05-ktor-exposed-integration/) | Exposed R2DBC repository와 application-owned pool을 사용하는 Ktor route | health/readiness와 CRUD를 H2 R2DBC pool로 검증 |
| [`06-spring-modulith-publications`](06-spring-modulith-publications/) | custom publication log를 사용하는 Spring Modulith 형태의 order/fulfillment handoff | H2 R2DBC order/log transaction과 coroutine dispatcher |
| [`07-ddd-aggregate-repository`](07-ddd-aggregate-repository/) | value object aggregate, 순서가 있는 domain event, atomic repository rollback | H2 R2DBC aggregate, line, event table |
| [`08-ddd-modulith-boundaries`](08-ddd-modulith-boundaries/) | orders와 shipping 사이의 named-interface boundary verification | H2 R2DBC event handoff와 valid/invalid Modulith 검증 |

각 예제 README는 local schema, R2DBC trade-off, verification contract를 설명하므로
Source를 먼저 열지 않아도 flow를 확인할 수 있습니다.

## R2DBC 범위

CockroachDB는 PostgreSQL wire protocol을 사용하므로 production deployment에서는
PostgreSQL R2DBC driver를 사용하고 CockroachDB retry contract를 transaction
boundary에 적용할 수 있습니다. Workshop의 기본 실행은 local, deterministic하게
유지합니다. Test는 H2 R2DBC와 synthetic SQLSTATE `40001` failure로 retryable
serialization failure만 재실행되는지 검증합니다.

Ktor 예제는 `ConnectionPool` lifecycle을 application이 소유하고 repository
접근에 `suspendTransaction`만 사용합니다. Spring Modulith 예제는 공식
`EventPublicationRepository` SPI가 synchronous이고 R2DBC 구현을 제공하지 않기
때문에 custom publication log를 기록합니다. DDD 예제는 aggregate event sequence,
commit/rollback, named-interface boundary violation을 local H2 R2DBC test로
실행합니다.

삭제한 source 예제는 local adapter stand-in으로 대체하지 않습니다.

| `exposed-workshop` 예제 | 13장 결정 |
|---|---|
| `01-bigquery-dry-run` | 제외; BigQuery dry-run은 R2DBC가 아니라 client/API 중심 |
| `02-trino-session-options` | 제외; Trino session option은 기본 R2DBC 경로가 아니라 Trino client/coordinator protocol 영역 |
| `03-cockroachdb-retry` | PostgreSQL 호환 R2DBC transaction retry behavior로 cover |
| `04-starrocks-olap-local` | 제외; 기본 StarRocks R2DBC driver가 있다고 암시하지 않음 |
| `09-duckdb-embedded-analytics` | 제외; 이 workshop에서는 DuckDB를 embedded/JDBC 중심 analytics engine으로 취급 |
| `05-ktor-exposed-integration` | Ktor R2DBC integration module로 cover |
| `06-spring-modulith-publications` | custom R2DBC publication-log module로 cover; native SPI는 synchronous로 유지 |
| `07-ddd-aggregate-repository` | atomic DDD aggregate repository module로 cover |
| `08-ddd-modulith-boundaries` | valid/invalid Spring Modulith boundary module로 cover |

## 검증

```bash
./gradlew projects --console=plain
repo-test-summary -- ./gradlew :03-cockroachdb-retry:test :05-ktor-exposed-integration:test :06-spring-modulith-publications:test :07-ddd-aggregate-repository:test :08-ddd-modulith-boundaries:test -PuseDB=H2 --continue --console=plain
```

`.github/workflows/Examples.yml`는 Chapter 13 파일, root README, shared
infrastructure, Gradle 파일, workflow 자체가 바뀔 때 같은 5개 모듈 Chapter 13
H2 smoke coverage를 실행합니다.
