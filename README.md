# Exposed R2DBC Workshop (Kotlin Exposed R2DBC 학습 자료)

이 저장소는 Kotlin Exposed의 R2DBC 기반 예제와 워크숍을 모아둔 컬렉션입니다. Reactive 환경에서 Exposed를 어떻게 사용하는지 단계별로 살펴볼 수 있습니다.

## Kotlin Exposed R2DBC란?

Kotlin Exposed는 Kotlin 언어로 작성된 SQL 프레임워크입니다. R2DBC 환경에서는 비동기/논블로킹 방식으로 데이터베이스와 통신하며, Kotlin의 타입 안전성과 함께 반응형 데이터 접근을 제공합니다.

### R2DBC의 주요 특징

| 특징                  | 설명                                |
|---------------------|-----------------------------------|
| **논블로킹 I/O**        | 완전한 비동기/논블로킹 데이터베이스 접근            |
| **Reactive Stream** | Reactor, RxJava, Coroutines 완벽 지원 |
| **Backpressure**    | 데이터 스트림의 배압(Backpressure) 제어      |
| **타입 안전성**          | 컴파일 타임에 SQL 오류 감지                 |
| **다양한 DB 지원**       | H2, MySQL, PostgreSQL, MariaDB 등  |

## 학습 가이드

이 워크숍은 다음과 같은 순서로 학습하는 것을 권장합니다:

1. **Spring Boot**: Spring WebFlux + Exposed R2DBC 기본 통합
2. **Exposed 기본**: R2DBC 환경에서의 SQL DSL 사용법
3. **DDL/DML**: 스키마 정의와 데이터 조작
4. **고급 기능**: 암호화, JSON, 커스텀 타입, 날짜/시간 처리
5. **JPA 마이그레이션**: JPA 코드를 Exposed R2DBC로 변환
6. **비동기 처리**: Coroutines, Virtual Threads
7. **Spring 통합**: Repository 패턴, 캐시
8. **멀티테넌시**: 다중 테넌트 아키텍처
9. **고성능**: 캐시 전략

## 상세 문서

모든 예제의 상세 설명은 [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)에서 확인할 수 있습니다.

---

## 모듈 목록

### 공유 라이브러리

#### [Exposed R2DBC Shared](00-shared/exposed-r2dbc-shared/README.md)

`exposed-r2dbc-workshop` 프로젝트 전반에서 사용되는 공통 테스트 유틸리티와 리소스를 제공합니다. 다양한 데이터베이스 환경에서 일관된 테스트를 수행할 수 있도록 지원합니다.

---

### Spring Boot 통합

#### [Spring WebFlux with Exposed R2DBC](01-spring-boot/spring-webflux-exposed/README.md)

Spring WebFlux + Kotlin Coroutines + Exposed R2DBC를 이용하여 비동기 REST API를 구축하는 방법을 학습합니다. 반응형 프로그래밍 모델과 Exposed의 통합 방법을 익힙니다.

---

### Exposed R2DBC 기본

#### [Exposed R2DBC SQL Example](03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md)

Exposed의 SQL DSL(Domain Specific Language)을 R2DBC 환경에서 사용하는 방법을 학습합니다. 타입 안전한 SQL 쿼리 작성 방법과 DSL의 장점을 익힙니다.

---

### Exposed R2DBC DDL (스키마 정의)

#### [Connection Management](04-exposed-r2dbc-ddl/01-connection/README.md)

R2DBC 환경에서 데이터베이스 연결 설정, 예외 처리, 타임아웃, 커넥션 풀링 등 연결 관리의 핵심 개념을 학습합니다.

#### [Schema Definition Language (DDL)](04-exposed-r2dbc-ddl/02-ddl/README.md)

Exposed의 DDL 기능을 R2DBC 환경에서 학습합니다. 테이블, 컬럼, 인덱스 정의 방법을 익힙니다.

---

### Exposed R2DBC DML (데이터 조작)

#### [DML 기본 연산](05-exposed-r2dbc-dml/01-dml/README.md)

R2DBC 환경에서 SELECT, INSERT, UPDATE, DELETE의 기본 패턴을 학습합니다. 조건식, 서브쿼리, 페이징 등 실무에서 자주 사용하는 패턴을 익힙니다.

#### [컬럼 타입](05-exposed-r2dbc-dml/02-types/README.md)

Exposed R2DBC에서 제공하는 다양한 컬럼 타입을 학습합니다. 기본 타입부터 배열, BLOB, UUID까지 폭넓게 다룹니다.

#### [SQL 함수](05-exposed-r2dbc-dml/03-functions/README.md)

Exposed R2DBC 쿼리에서 다양한 SQL 함수를 사용하는 방법을 학습합니다. 집계 함수, 문자열 함수 등을 다룹니다.

#### [트랜잭션 관리](05-exposed-r2dbc-dml/04-transactions/README.md)

Exposed R2DBC의 트랜잭션 관리 기능을 학습합니다. 격리 수준, 중첩 트랜잭션, 롤백, 코루틴 통합 등을 다룹니다.

---

### 고급 기능

#### [Exposed R2DBC Crypt (투명한 컬럼 암호화)](06-advanced/01-exposed-r2dbc-crypt/README.md)

`exposed-crypt` 확장을 사용하여 R2DBC 환경에서 데이터베이스 컬럼을 투명하게 암호화/복호화하는 방법을 학습합니다.

#### [Exposed R2DBC JavaTime (java.time 통합)](06-advanced/02-exposed-r2dbc-javatime/README.md)

Java 8의 `java.time` API와 Exposed R2DBC의 통합 방법을 학습합니다.

#### [Exposed R2DBC Kotlinx-Datetime](06-advanced/03-exposed-r2dbc-kotlin-datetime/README.md)

`kotlinx.datetime` 라이브러리와 Exposed R2DBC의 통합 방법을 학습합니다. 멀티플랫폼 프로젝트에 적합합니다.

#### [Exposed R2DBC Json (JSON/JSONB 지원)](06-advanced/04-exposed-r2dbc-json/README.md)

`exposed-json` 모듈을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 다루는 방법을 학습합니다.

#### [Exposed R2DBC Money (금융 데이터 처리)](06-advanced/05-exposed-r2dbc-money/README.md)

`exposed-money` 모듈을 사용하여 R2DBC 환경에서 통화 값을 안전하게 처리하는 방법을 학습합니다.

#### [커스텀 컬럼 타입](06-advanced/06-exposed-r2dbc-custom-columns/README.md)

사용자 정의 컬럼 타입을 구현하는 방법을 학습합니다. 암호화, 압축, 직렬화 등의 투명한 변환을 구현합니다.

#### [커스텀 Entity (ID 생성 전략)](06-advanced/07-exposed-r2dbc-custom-entities/README.md)

Snowflake, KSUID, Time-based UUID 등 다양한 ID 생성 전략을 가진 커스텀 Entity를 구현합니다.

#### [Exposed R2DBC Jackson (Jackson 기반 JSON)](06-advanced/08-exposed-r2dbc-jackson/README.md)

Jackson 라이브러리를 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리하는 방법을 학습합니다.

#### [Exposed R2DBC Fastjson2](06-advanced/09-exposed-r2dbc-fastjson2/README.md)

Alibaba Fastjson2 라이브러리를 사용하여 JSON 컬럼을 처리하는 방법을 학습합니다.

#### [Exposed R2DBC Jasypt (결정적 암호화)](06-advanced/10-exposed-r2dbc-jasypt/README.md)

Jasypt를 사용하여 R2DBC 환경에서 검색 가능한(결정적) 암호화를 구현하는 방법을 학습합니다.

#### [Exposed R2DBC Jackson 3](06-advanced/11-exposed-r2dbc-jackson3/README.md)

Jackson 3.x 버전을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리하는 방법을 학습합니다.

---

### JPA 마이그레이션

#### [JPA 기본 기능 변환](07-jpa-convert/01-convert-jpa-basic/README.md)

JPA의 기본 기능을 Exposed R2DBC로 구현하는 방법을 학습합니다. Entity, 연관관계, 기본키 등을 다룹니다.

---

### 코루틴 & 가상 스레드

#### [Coroutines 기본](08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md)

Exposed R2DBC를 Kotlin Coroutines 환경에서 사용하는 방법을 학습합니다. `suspend` 함수와 Flow를 활용한 비동기 처리를 다룹니다.

#### [Virtual Threads 기본](08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md)

Exposed R2DBC를 Java 21 Virtual Threads 환경에서 사용하는 방법을 학습합니다. 블로킹 코드 스타일을 유지하면서 고성능 비동기 처리를 구현합니다.

---

### Spring 통합

#### [Exposed R2DBC Repository (코루틴)](09-spring/05-exposed-r2dbc-repository-coroutines/README.md)

코루틴 환경에서 Repository 패턴을 사용하여 비동기 데이터 접근을 구현합니다. Spring Data Repository 스타일의 인터페이스를 정의하고 구현하는 방법을 학습합니다.

#### [Suspended Cache](09-spring/07-spring-suspended-cache/README.md)

Lettuce를 활용한 Suspended Cache를 코루틴 환경에서 Exposed R2DBC와 함께 사용하는 방법을 학습합니다.

---

### 멀티테넌시

#### [Spring WebFlux + Multitenant](10-multi-tenant/03-multitenant-spring-webflux/README.md)

WebFlux와 Coroutines를 이용하여 반응형 멀티테넌시를 구현하는 방법을 학습합니다. Schema-based Multi-tenancy 패턴을 다룹니다.

---

### 고성능

#### [캐시 전략 (코루틴)](11-high-performance/02-cache-strategies-r2dbc/README.md)

R2DBC 환경에서 비동기로 작동하는 다양한 캐시 전략(Read Through, Write Through, Write Behind)을 구현합니다.

---

## 시작하기

### 사전 요구사항

- JDK 21 이상
- Gradle 8.x 이상
- Docker (Testcontainers 사용 시)

### 프로젝트 빌드

```bash
# 전체 프로젝트 빌드
./gradlew build

# 특정 모듈 테스트 실행
./gradlew :05-exposed-r2dbc-dml:01-dml:test
```

### IDE 설정

IntelliJ IDEA를 권장합니다. Kotlin 플러그인이 설치되어 있어야 합니다.

## 기여하기

이 프로젝트는 학습 목적으로 제작되었습니다. 오타 수정, 예제 추가, 번역 개선 등 모든 기여를 환영합니다.

## 라이선스

Apache License 2.0
