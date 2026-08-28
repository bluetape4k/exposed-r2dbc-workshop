# Issue #213 Exposed R2DBC Caffeine 예제 설계

## 문제와 목표

Issue #213은 `bluetape4k-exposed-r2dbc-caffeine` provider를 Exposed R2DBC
애플리케이션에서 사용하는 별도 학습 예제를 요구한다. 기존
`11-high-performance/02-cache-strategies-r2dbc`는 Spring WebFlux와 Redisson
캐시를 다루고, `04-cache-strategies-ktor-r2dbc`와
`05-cache-strategies-ktor-r2dbc-coroutines`는 Ktor에서 Redisson을 다룬다.
따라서 이번 예제는 같은 Ktor 표면을 유지하되 Caffeine provider의
`AbstractR2dbcCaffeineRepository`를 직접 연결해서 다음 세 가지 쓰기 모드를
같은 `ProductRecord` 키/값 계약으로 비교한다.

- `READ_ONLY`: 읽기 miss는 DB에서 read-through하고 `put`은 캐시만 갱신한다.
- `WRITE_THROUGH`: 캐시와 DB를 같은 suspend 호출에서 갱신한다.
- `WRITE_BEHIND`: 캐시에 먼저 반영하고 bounded queue를 거쳐 DB에 비동기
  반영한다. `close()`는 최종 drain을 기다린다.

예제는 교육용 H2 R2DBC 경로만 제공한다. 외부 pool, Redis,
`R2dbcCaffeineSnapshotCache` 기준 데이터 캐시,
자동 재시도, crash durability, exactly-once, mutex lifecycle 보장은 포함하지
않는다. provider tag `1.12.1`의 현재 계약과 비보장 사항을 README에 명시한다.

## 현재 근거와 영향 범위

| 근거 | 확인 내용 | 설계 영향 |
|---|---|---|
| Issue #213 live metadata | OPEN, milestone `1.4.0`, labels `enhancement`, `test`, `dependencies`, `examples`, assignee `debop` | 새 sibling 모듈과 PR metadata를 issue와 동일하게 맞춘다. |
| 중앙 catalog `bluetape4k-dependencies` tag `1.4.0` | `bluetape4k-exposed-r2dbc-caffeine` alias와 Exposed BOM `1.12.1` | 로컬 catalog에는 버전 없는 alias만 추가한다. |
| provider `bluetape4k-exposed` tag `1.12.1` | `AbstractR2dbcCaffeineRepository`, `LocalCacheConfig`, `CacheWriteMode`, `validateConsistency`, write-behind drain/close | 애플리케이션 코드는 provider 계약을 감싸고 unsupported guarantee를 만들지 않는다. |
| 현재 sibling `11-high-performance/04`, `05`, `06` | Ktor plugin, H2 R2DBC pool, `ApplicationStopped` lifecycle | 같은 app-owned pool 패턴을 재사용한다. |
| `exposed-workshop/11-high-performance/06-cache-strategies-coroutines-ktor` | JDBC의 SKU 기반 Product 예제와 cache lifecycle | 도메인 경계와 route 의도만 빌리고 JDBC 구현을 복제하지 않는다. |
| 현재 `settings.gradle.kts` | chapter leaf directory 자동 검색 | 새 디렉터리 생성으로 Gradle project를 등록한다. |
| `.github/workflows/Examples.yml` | chapter 11 paths, task, artifact paths가 02–06으로 고정 | push/PR path, task, artifact에 07을 각각 추가한다. |

## 선택한 접근

### 접근 A — Ktor + provider 직접 연결 (권장)

새 sibling `11-high-performance/07-cache-strategies-r2dbc-caffeine`에 Ktor
Netty 애플리케이션을 둔다. `R2dbcCaffeineResources`가
`ConnectionPool`과 `R2dbcDatabase`를 함께 소유하고, provider가 기본
`suspendTransaction`을 사용할 수 있도록 `TransactionManager` 경계를
설정한다. `ProductCaffeineRepository`는
`AbstractR2dbcCaffeineRepository<String, ProductRecord>`를 상속하고
`ProductTable` 매핑 네 함수(`toEntity`, `updateEntity`, `insertEntity`,
`extractId`)만 구현한다. route/service는 provider가 반환하는 같은
키/값을 유지하면서 관찰 가능한 상태만 추가한다.

장점은 provider의 실제 R2DBC 경계, Ktor의 suspend route, app-owned pool 및
shutdown 순서를 한 화면에서 확인할 수 있다는 점이다. 단점은 provider의
write-behind 비동기 상태를 테스트할 polling/close 보조 코드가 필요하다는
점이며, 그 코드는 테스트와 예제에 한정한다.

### 접근 B — Spring WebFlux controller로 provider를 감싼다

기존 02 모듈과 유사한 controller를 추가하는 방법이다. 하지만 02가 이미
Spring WebFlux 기반 R2DBC 캐시 전략을 제공하고, provider 차이보다
controller 차이가 전면에 드러난다. 새 adapter의 lifecycle과 suspend
transaction 경계를 학습하기에는 중복이 크므로 선택하지 않는다.

### 접근 C — HTTP 없는 순수 repository fixture

provider 테스트처럼 H2 fixture와 repository만 추가하는 방법이다. 구현량은
작지만 app-owned pool, Ktor shutdown, 요청 취소 경계를 독자가 실행해 볼 수
없다. Issue가 요구하는 의미 있는 workshop 예제와 bilingual README/diagram
목적을 충족하지 못하므로 선택하지 않는다.

## 구성 요소와 책임

```text
Ktor route
  -> ProductCacheService (상태 관찰, 입력 검증, 실패 시 cache invalidation)
    -> ProductCaffeineRepository (provider adapter)
      -> Caffeine AsyncCache
      -> suspendTransaction -> app-owned R2DBC ConnectionPool -> H2
```

### `R2dbcCaffeineResources`

- H2 `ConnectionFactoryOptions`와 bounded `ConnectionPool`을 만든다.
- `R2dbcDatabase.connect(pool, config)`으로 database를 등록하고 현재
  애플리케이션의 기본 R2DBC database로 둔다.
- `ProductCaffeineRepository`와 database를 함께 소유한다.
- `close()` 순서는 `repository.close()` →
  `TransactionManager.closeAndUnregister(database)` → `pool.dispose()`다.
  repository가 write-behind 최종 flush를 마친 뒤 Exposed manager를
  해제해야 pool이 먼저 닫히지 않는다. `close()`는 idempotent하다.

### `ProductTable` / `ProductRecord`

SKU 문자열을 primary key로 사용한다. `ProductRecord(sku, name, version)`을
DB 행, Caffeine 값, JSON payload에 그대로 사용하여 세 모드의 키/값 계약을
단일화한다. seed는 `sku-1`, `sku-2` 두 행으로 결정적으로 만든다.

### `ProductCaffeineRepository`

provider의 abstract mapping만 구현한다. 모든 DB 접근은 provider 내부의
R2DBC `suspendTransaction`을 통과하며, 애플리케이션은 별도 JDBC 코드나
Redis client를 추가하지 않는다. `LocalCacheConfig`의 bounded queue와
`CacheWriteMode`를 resources 생성 인자로 노출한다.

### `ProductCacheService`와 route

- `GET /products/{sku}`: 캐시 상태를 먼저 확인하고 repository `get`을
  호출한다. miss는 DB read-through, null은 `404`다. 응답은
  `ProductReadResponse(product, cache)`이고 `cache`는 `HIT` 또는 `MISS`다.
- `PUT /products/{sku}`: JSON body `{"name":"..."}`를 받고 DB 원본을
  읽어 version을 증가시킨 뒤 repository `put`을 호출한다. 이름은 공백이
  아니며 120자를 넘지 않아야 하고, path SKU와 body에는 SKU를 중복해서
  받지 않는다. 존재하지 않는 SKU는 `404`, 잘못된 body는 `400`이다.
  응답은 갱신된 `ProductRecord`다. cancellation은 재던지고, 일반 오류는
  해당 키를 invalidate하여 DB를 기준 데이터 원본으로 남긴다.
- `DELETE /products/{sku}/cache`: 단일 키 invalidate.
- `DELETE /products/cache`: 전체 clear.
- `GET /products`: cache를 우회한 DB 목록.
- `GET /cache/health`: `validateConsistency()`의 mode, queue depth, worker
  state, 마지막 flush 오류를 JSON으로 노출한다.

요청/응답 모델의 일반 설명과 KDoc은 한국어로 작성하되 Kotlin identifier,
HTTP path, provider API 이름은 원문을 보존한다.

## 데이터 흐름과 실패 경계

1. startup에서 schema/seed를 `suspendTransaction(db = database)`로 초기화한다.
   초기화가 실패하면 애플리케이션이 자원을 누수하지 않도록 resources를
   닫은 뒤 예외를 다시 던진다.
2. read miss는 provider의 `AsyncCache.get` loader에서 DB를 읽고 동일한
   `ProductRecord`를 cache에 저장한다.
3. write-through는 provider의 즉시 DB 반영 결과를 반환한다.
4. write-behind는 bounded queue admission과 cache publication을 먼저
   완료한다. `validateConsistency()`의 `queueDepth`가 0이 되고
   `lastFlushError == null`일 때 drain 성공으로 관찰한다.
5. write-behind flush 오류는 provider가 보고하는 오류를 숨기지 않는다.
   자동 retry나 crash durability를 주장하지 않으며, README와 테스트는
   DB direct read, health 오류, invalidate 후 재조회로 DB 기준 데이터
   원본을 확인한다. 명시적 재시도는 실패한 repository를 닫고 새
   repository에서 유효한 값을 다시 `put`하는 운영 선택으로만 설명한다.
6. `CancellationException`은 일반 예외로 바꾸지 않는다. cancellation 중
   cache publication이나 queue admission이 실패하면 provider의 rollback과
   service의 invalidation을 관찰한다.
7. shutdown은 write-behind queue를 drain한 뒤 repository cache, Exposed
   manager, pool 순으로 종료한다. 종료 후 새 write는 허용하지 않는다.

## 실패 모드와 대응

| 실패 모드 | 관찰 가능한 증상 | 예제의 대응/비보장 |
|---|---|---|
| 존재하지 않는 SKU | `repository.get`이 `null` | route는 `404 NOT_FOUND`; 임의 key를 seed하지 않는다. |
| write-through DB 오류 | `put` 예외 | cancellation은 재던지고 일반 오류는 cache key를 invalidate한다. DB/cache 원자성을 provider 밖에서 주장하지 않는다. |
| write-behind queue admission 실패 또는 close 이후 write | `IllegalStateException`, cache publication 없음 | queue depth와 cache entry가 rollback되는지 테스트한다. 자동 retry는 제공하지 않는다. |
| write-behind flush 오류 | `lastFlushError`와 non-zero queue depth | direct DB read로 이전 값을 확인하고 health 오류를 노출한다. 실패 batch의 crash durability/exactly-once는 비보장이다. |
| pool보다 repository를 먼저 닫지 않음 | 최종 flush가 실패하거나 종료가 지연됨 | resources close 순서를 고정하고 idempotence/lifecycle 테스트를 둔다. |
| 요청 coroutine 취소 | `CancellationException`이 다른 오류로 변환됨 | cancellation propagation 테스트와 provider 경계 확인으로 방지한다. |

## 호환성·등록·문서

- 중앙 catalog가 제공하는 `libs.bluetape4k.exposed.r2dbc.caffeine` alias를
  사용하고 버전은 BOM에 맡긴다.
- 새 leaf directory는 `settings.gradle.kts` 자동 검색으로 등록되며,
  `./gradlew projects`에서 `:07-cache-strategies-r2dbc-caffeine`으로
  확인한다.
- `Examples.yml`의 push/PR path, chapter 11 test task, test-result artifact
  path를 07까지 확장한다.
- 모듈 README와 chapter/root README는 `README.md`/`README.ko.md`의
  source-equivalent 쌍으로 유지한다.
- architecture와 sequence diagram은
  `docs/images/readme-diagrams`에 English/Korean SVG+PNG 쌍으로 두고,
  README에는 PNG만 embed한다. `R2dbcCaffeineSnapshotCache`는 별도 provider
  contract로 설명하고 이번 모듈에는 구현하지 않는다.

## 수용 기준과 DoD

1. `ProductRecord`가 세 cache write mode에서 동일한 SKU key와 value를
   사용한다.
2. read-through hit/miss, unknown key, single/all invalidation, DB direct
   read를 route와 테스트에서 확인한다.
3. `READ_ONLY`, `WRITE_THROUGH`, `WRITE_BEHIND`의 DB 반영 시점과
   `validateConsistency()` 상태를 테스트한다.
4. cancellation은 `CancellationException`을 보존하고, close는 repository
   최종 drain → Exposed unregister → pool dispose 순서를 지킨다.
5. write-behind admission rollback, drain, flush 오류와 DB 기준 데이터
   원본 확인을 테스트한다. 자동 retry/crash durability/exactly-once를
   주장하지 않는다.
6. 중앙 alias, Gradle project, workflow task/path/artifact가 등록된다.
7. module/chapter/root README EN/KO, Korean KDoc, upstream current status와
   non-guarantees, `R2dbcCaffeineSnapshotCache` 분리가 source와 일치한다.
8. architecture/sequence SVG+PNG 쌍이 렌더·semantic·geometry·visual·pair
   audit를 통과하고 full-size PNG를 사람이 확인한다.
9. targeted tests, chapter H2 tests, project discovery, changed-task
   selector, Detekt/static checks, actionlint, diff hygiene가 통과한다.
10. Lore commit, Korean PR metadata/`## DoD Status`, exact-head CI/review
    read-back, fresh rebase-merge approval, merge SHA, local sync/cleanup가
    workflow gate를 충족한다.

## 결정

접근 A를 채택하고 `R2dbcCaffeineSnapshotCache` 기준 데이터 캐시는 이번
범위에서 제외한다. provider tag
`1.12.1`의 실제 `AbstractR2dbcCaffeineRepository` 동작을 얇은 Ktor
애플리케이션으로 노출하면 기존 Redisson 예제와 중복을 피하면서 Issue가
요구한 cache mode와 lifecycle 경계를 재현할 수 있다.
