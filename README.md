# Exposed R2DBC Workshop (Kotlin Exposed R2DBC 학습 자료)

이 저장소는 Kotlin Exposed 1.0.0의 R2DBC 기반 예제와 워크숍을 모아둔 컬렉션입니다. Reactive 환경에서 Exposed를 어떻게 사용하는지 단계별로 살펴볼 수 있습니다.

## Kotlin Exposed R2DBC란?

Kotlin Exposed는 Kotlin 언어로 작성된 SQL 프레임워크입니다. R2DBC 환경에서는 비동기/논블로킹 방식으로 데이터베이스와 통신하며, Kotlin의 타입 안전성과 함께 반응형 데이터 접근을 제공합니다.

## Documents

예제들의 상세 설명은 [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)에서 확인할 수 있습니다.

## Modules

## Shared

### [Exposed R2DBC Shared](00-shared/exposed-r2dbc-shared/README.md)

`exposed-r2dbc-workshop` 전반에서 사용하는 공통 테스트 유틸리티와 공유 리소스를 제공합니다.

## Spring Boot

### [Spring WebFlux with Exposed R2DBC](01-spring-boot/spring-webflux-exposed/README.md)

Spring WebFlux 환경에서 Exposed R2DBC로 비동기 데이터 접근을 구현하는 예제를 제공합니다.

## Exposed R2DBC Basic

### [Exposed R2DBC SQL Example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)

Exposed의 SQL DSL을 R2DBC 환경에서 사용하는 기본 예제를 제공합니다.

## Exposed R2DBC DDL

### [Connection Management](04-exposed-r2dbc-ddl/01-connection/README.md)

Exposed R2DBC에서 커넥션을 구성하고 관리하는 방법을 다룹니다.

### [Schema Definition Language (DDL)](04-exposed-r2dbc-ddl/02-ddl/README.md)

Exposed의 DDL 기능을 R2DBC 환경에서 활용하는 예제를 제공합니다.

## Exposed R2DBC DML

### [DML Basic Operations](05-exposed-r2dbc-dml/01-dml/README.md)

R2DBC 환경에서의 기본 DML 연산을 다룹니다.

### [Column Types](05-exposed-r2dbc-dml/02-types/README.md)

Exposed R2DBC에서 지원하는 다양한 컬럼 타입 사용 예제를 제공합니다.

### [Functions](05-exposed-r2dbc-dml/03-functions/README.md)

SQL 함수 사용 예제를 R2DBC 환경에서 설명합니다.

### [Transaction Management](05-exposed-r2dbc-dml/04-transactions/README.md)

Exposed R2DBC의 트랜잭션 관리 방법을 다룹니다.

## Advanced

### [Exposed R2DBC Crypt](06-advanced/01-exposed-r2dbc-crypt/README.md)

암호화 모듈을 Exposed R2DBC에 통합하는 방법을 다룹니다.

### [Exposed R2DBC JavaTime](06-advanced/02-exposed-r2dbc-javatime/README.md)

`java.time` 타입과 Exposed R2DBC의 통합 방법을 제공합니다.

### [Exposed R2DBC Kotlinx-Datetime](06-advanced/03-exposed-r2dbc-kotlin-datetime/README.md)

`kotlinx.datetime` 타입을 Exposed R2DBC에서 사용하는 방법을 설명합니다.

### [Exposed R2DBC JSON](06-advanced/04-exposed-r2dbc-json/README.md)

`json`, `jsonb` 컬럼을 Exposed R2DBC에서 사용하는 방법을 다룹니다.

### [Exposed R2DBC Money](06-advanced/05-exposed-r2dbc-money/README.md)

`Money` 타입을 Exposed R2DBC에서 다루는 예제를 제공합니다.

### [Exposed R2DBC Custom Columns](06-advanced/06-exposed-r2dbc-custom-columns/README.md)

사용자 정의 컬럼 타입 구현 방법을 보여줍니다.

### [Exposed R2DBC Custom Entities](06-advanced/07-exposed-r2dbc-custom-entities/README.md)

`IntIdEntity`, `LongIdEntity`, `UUIDIdEntity` 외 커스텀 Entity 구현 예제를 제공합니다.

### [Exposed R2DBC Jackson](06-advanced/08-exposed-r2dbc-jackson/README.md)

JSON 컬럼을 Jackson으로 직렬화/역직렬화하는 방법을 다룹니다.

### [Exposed R2DBC Fastjson2](06-advanced/09-exposed-r2dbc-fastjson2/README.md)

JSON 컬럼을 Fastjson2로 직렬화/역직렬화하는 방법을 다룹니다.

### [Exposed R2DBC Jasypt](06-advanced/10-exposed-r2dbc-jasypt/README.md)

Jasypt 기반 암호화를 Exposed R2DBC에 통합하는 방법을 설명합니다.

### [Exposed R2DBC Jackson 3](06-advanced/11-exposed-r2dbc-jackson3/README.md)

JSON 컬럼을 Jackson 3.x로 직렬화/역직렬화하는 방법을 다룹니다.

## JPA Convert

### [Convert JPA Basic to Exposed R2DBC](07-jpa-convert/01-convert-jpa-basic/README.md)

기본적인 JPA 기능을 Exposed R2DBC로 변환하는 예제를 제공합니다.

## R2DBC Coroutines

### [Coroutines Basic](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md)

Coroutines 환경에서 Exposed R2DBC를 사용하는 방법을 소개합니다.

### [Virtual Threads Basic](08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md)

Java 21 Virtual Threads 환경에서 Exposed R2DBC를 사용하는 방법을 다룹니다.

## Spring

### [Exposed R2DBC Repository with Coroutines](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)

Repository 패턴과 Coroutines를 활용한 Exposed R2DBC 예제를 제공합니다.

### [Spring Boot Suspended Cache](09-spring/07-spring-suspended-cache/README.md)

Lettuce 기반 Suspended Cache를 Coroutines 환경에서 Exposed R2DBC와 함께 사용하는 방법을 다룹니다.

## Multi Tenant

### [Spring WebFlux Multitenant](10-multi-tenant/03-multitenant-spring-webflux/README.md)

Spring WebFlux + Coroutines + Exposed R2DBC로 멀티테넌시를 구현하는 방법을 설명합니다.

## High Performance

### [Caching Strategies with R2DBC](11-high-performance/02-cache-strategies-r2dbc/README.md)

R2DBC 환경에서 다양한 캐시 전략을 적용하는 예제를 제공합니다.
