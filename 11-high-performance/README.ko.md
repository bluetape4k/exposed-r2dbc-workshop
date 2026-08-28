> English version: [README.md](README.md)

# 11. High Performance

Exposed R2DBC 환경에서 성능과 확장성을 높이기 위한 예제를 모아둔 섹션입니다.  
이 디렉터리의 예제는 단순 CRUD보다 캐시, 라우팅, 읽기/쓰기 분리처럼 운영 환경에 가까운 주제를 다룹니다.

## 고성능 전략 개요

![11 high performance Architecture diagram](../docs/images/readme-diagrams/11-high-performance-architecture-01.png)

## 캐시 계층 구조 (클래스 다이어그램)

![( ) diagram](../docs/images/readme-diagrams/11-high-performance-class-02.png)

## 캐시 히트/미스 처리 흐름

![/ diagram](../docs/images/readme-diagrams/11-high-performance-sequence-03.png)

## 학습 목표

- Redis/Redisson 기반 캐시 계층을 Coroutines와 함께 적용한다.
- 요청 컨텍스트를 이용해 tenant + read/write 라우팅을 구현한다.
- Spring WebFlux와 Exposed R2DBC 조합에서 병목이 생기기 쉬운 지점을 파악한다.

## 하위 모듈

| 모듈 | 주제 | 언제 보면 좋은가 |
|---|---|---|
| [02-cache-strategies-r2dbc](./02-cache-strategies-r2dbc/README.md) | Read Through, Write Through, Write Behind, Read-Only 캐시 전략 | DB 부하를 캐시로 완화하는 패턴이 궁금할 때 |
| [03-routing-datasource](./03-routing-datasource/README.md) | tenant + read/write 분리 라우팅, Reactor Context 전파 | 읽기/쓰기 분리, 샤딩, 멀티 테넌트 라우팅의 기초를 보고 싶을 때 |
| [04-cache-strategies-ktor-r2dbc](./04-cache-strategies-ktor-r2dbc/README.ko.md) | Ktor route에서 관찰하는 cache hit/miss, invalidation, write refresh, DB fallback | Spring WebFlux controller 없이 일반 캐시 전략을 보고 싶을 때 |
| [05-cache-strategies-ktor-r2dbc-coroutines](./05-cache-strategies-ktor-r2dbc-coroutines/README.ko.md) | Ktor coroutine single-flight cache fallback, cancellation, coalesced read | 일반 Ktor cache 예제와 분리해 coroutine 전용 cache 동작을 보고 싶을 때 |
| [06-routing-datasource-ktor-r2dbc](./06-routing-datasource-ktor-r2dbc/README.ko.md) | Reactor Context 없는 Ktor tenant + read/write R2DBC target routing | Routing datasource 주제를 Ktor request handling으로 보고 싶을 때 |
| [07-cache-strategies-r2dbc-caffeine](./07-cache-strategies-r2dbc-caffeine/README.ko.md) | Exposed R2DBC Caffeine adapter, 세 가지 write mode, bounded write-behind lifecycle | provider 기반 local cache와 직접 DB/health 검증을 함께 보고 싶을 때 |

## 권장 순서

1. `02-cache-strategies-r2dbc`로 캐시 적중/미스 흐름을 먼저 확인합니다.
2. `03-routing-datasource`로 요청 컨텍스트 기반 라우팅을 확인합니다.
3. `04-cache-strategies-ktor-r2dbc`로 같은 캐시 전략 표면을 Ktor route에서 비교합니다.
4. `05-cache-strategies-ktor-r2dbc-coroutines`에서 single-flight fallback과 cancellation 동작을 확인합니다.
5. `06-routing-datasource-ktor-r2dbc`에서 Ktor call attributes와 Spring Reactor Context routing을 비교합니다.
6. `07-cache-strategies-r2dbc-caffeine`에서 provider 기반 Caffeine adapter, write mode, 자원 종료를 비교합니다.
7. 필요하면 `09-spring`, `10-multi-tenant` 모듈과 함께 비교해 패턴 차이를 봅니다.

## 실행 팁

```bash
# 캐시 전략 모듈 테스트
./gradlew :02-cache-strategies-r2dbc:test

# 라우팅 데이터소스 모듈 테스트
./gradlew :03-routing-datasource:test

# Ktor cache strategy 모듈 테스트
./gradlew :04-cache-strategies-ktor-r2dbc:test

# Ktor coroutine cache strategy 모듈 테스트
./gradlew :05-cache-strategies-ktor-r2dbc-coroutines:test

# Ktor routing datasource 모듈 테스트
./gradlew :06-routing-datasource-ktor-r2dbc:test

# Exposed R2DBC Caffeine adapter 테스트
./gradlew :07-cache-strategies-r2dbc-caffeine:test -PuseDB=H2
```

이 명령들은 `./gradlew projects`가 보여주는 고유한 Gradle project name을
사용합니다. `:exposed-r2dbc-11-high-performance-...` 같은 prefix 이름은 이
repository에 등록되어 있지 않습니다.
