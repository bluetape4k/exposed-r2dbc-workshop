# Full Module Review & Documentation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** exposed-r2dbc-workshop 전체 모듈에 대해 코드 리뷰 + 테스트 KDoc 보강 + README 개선을 병렬로 수행한다.

**Architecture:** 7개 청크로 나누어 독립적 모듈 그룹별로 병렬 subagent를 투입한다. 각 subagent는 (1) 테스트 파일 읽기 → (2) 코드 이슈 수정 → (3) KDoc 누락 보완 → (4) README 개선 → (5) 커밋 순서로 작업한다.

**Tech Stack:** Kotlin 2.2, JetBrains Exposed v1 R2DBC, JUnit5 ParameterizedTest, KDoc, Markdown

---

## 공통 작업 기준

각 모듈에서 수행할 작업 기준:

### 코드 리뷰 체크리스트
- [ ] 불필요한 `!!` (non-null assertion) → `requireNotNull` 또는 safe call 로 교체
- [ ] `runBlocking` 대신 `runTest` 사용 여부 확인
- [ ] `withTables` / `withDb` 중첩 불필요한 구조 정리
- [ ] `companion object` 에 `KLoggingChannel()` 누락 여부
- [ ] `log.debug { }` 람다 형식 사용 여부 (문자열 연결 사용 금지)
- [ ] `@ParameterizedTest @MethodSource(ENABLE_DIALECTS_METHOD)` 누락 여부
- [ ] 테스트 메서드명 한국어 백틱 스타일 일관성

### KDoc 체크리스트
- [ ] 클래스 수준 KDoc: 무엇을 학습하는지 (주요 학습 내용 bullet), DB 호환성 명시
- [ ] 메서드 수준 KDoc: 동작 설명 + SQL 예제 (최소 Postgres 기준) 포함
- [ ] `@see` 태그로 관련 Exposed API 참조

### README 체크리스트
- [ ] 모듈 목적 1-2문장 설명
- [ ] 학습 목표 (bullet 3-5개)
- [ ] 핵심 코드 예제 (Kotlin + SQL 쌍으로)
- [ ] 테스트 실행 명령어
- [ ] 관련 모듈/문서 링크

---

## Chunk 1: 00-shared + 04-ddl (01-connection, 02-ddl)

### Task 1: 00-shared/exposed-r2dbc-shared

**Files to review/modify:**
- `00-shared/exposed-r2dbc-shared/README.md`
- `src/test/kotlin/exposed/r2dbc/shared/tests/AbstractR2dbcExposedTest.kt`
- `src/test/kotlin/exposed/r2dbc/shared/tests/TestDB.kt`
- `src/test/kotlin/exposed/r2dbc/shared/dml/DMLTestData.kt`

- [ ] **Step 1: 파일 읽기**
  ```bash
  cat 00-shared/exposed-r2dbc-shared/src/test/kotlin/exposed/r2dbc/shared/tests/AbstractR2dbcExposedTest.kt
  cat 00-shared/exposed-r2dbc-shared/src/test/kotlin/exposed/r2dbc/shared/tests/TestDB.kt
  cat 00-shared/exposed-r2dbc-shared/src/test/kotlin/exposed/r2dbc/shared/dml/DMLTestData.kt
  ```

- [ ] **Step 2: AbstractR2dbcExposedTest KDoc 보강**
  - 클래스 KDoc: 모든 테스트 클래스의 기반, UTC 고정, enableDialects() 설명
  - `withDb`, `withTables`, `enableDialects` 메서드 KDoc 추가

- [ ] **Step 3: TestDB KDoc 보강**
  - 각 enum 상수에 설명 추가 (H2, H2_MYSQL, H2_PSQL, H2_MARIADB, MARIADB, MYSQL_V8, POSTGRESQL)
  - `ALL_H2`, `ALL_POSTGRES`, `ALL_MYSQL_MARIADB` 상수 설명

- [ ] **Step 4: DMLTestData KDoc 보강**
  - `withCitiesAndUsers`, `withSales`, `withSalesAndSomeAmounts` KDoc + SQL 예제

- [ ] **Step 5: README 개선**
  - AbstractR2dbcExposedTest 사용 예제 코드블록 추가
  - TestDB enum 표 추가
  - withTables vs withDb 차이점 설명

- [ ] **Step 6: 커밋**
  ```bash
  git add 00-shared/
  git commit -m "docs: 00-shared 모듈 KDoc 및 README 보강"
  ```

---

### Task 2: 04-ddl/01-connection

**Files:**
- `04-exposed-r2dbc-ddl/01-connection/src/test/kotlin/exposed/r2dbc/examples/connection/Ex01_Connection.kt`
- `04-exposed-r2dbc-ddl/01-connection/src/test/kotlin/exposed/r2dbc/examples/connection/h2/Ex01_H2_ConnectionPool.kt`
- `04-exposed-r2dbc-ddl/01-connection/src/test/kotlin/exposed/r2dbc/examples/connection/h2/Ex02_H2_MultiDatabase.kt`
- `04-exposed-r2dbc-ddl/01-connection/README.md`

- [ ] **Step 1: 파일 읽기 및 코드 이슈 파악**

- [ ] **Step 2: 코드 이슈 수정**
  - `!!` 사용 검토 및 교체
  - `companion object` 확인

- [ ] **Step 3: 각 테스트 파일 KDoc 보강**
  - Ex01_Connection: 클래스 KDoc + 각 메서드에 ConnectionFactory 설정 예제
  - Ex01_H2_ConnectionPool: H2 커넥션 풀 설정 파라미터 설명
  - Ex02_H2_MultiDatabase: 다중 DB 연결 패턴 설명

- [ ] **Step 4: README 개선**
  - R2DBC ConnectionFactory 설정 예제 코드블록 추가
  - H2 인메모리 vs 파일 DB 연결 차이 설명
  - application.yml R2DBC 설정 예제

- [ ] **Step 5: 커밋**
  ```bash
  git add 04-exposed-r2dbc-ddl/01-connection/
  git commit -m "docs: 04-ddl/01-connection KDoc 및 README 보강"
  ```

---

### Task 3: 04-ddl/02-ddl

**Files:**
- `Ex01_CreateDatabase.kt`, `Ex02_CreateTable.kt`, `Ex03_CreateMissingTableAndColumns.kt`
- `Ex04_ColumnDefinition.kt`, `Ex05_CreateIndex.kt`, `Ex06_Sequence.kt`
- `Ex07_CustomEnumeration.kt`, `Ex10_DDL_Examples.kt`
- `README.md`

- [ ] **Step 1: 각 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - `Assumptions.assumeTrue { }` 람다 형식 사용 일관성 확인
  - `log.debug { }` 람다 형식 확인

- [ ] **Step 3: KDoc 보강**
  - 각 파일 클래스 KDoc: 학습 목표 + 지원 DB
  - 메서드별 KDoc: SQL DDL 예제 (없는 메서드에 추가)
  - Ex04_ColumnDefinition: 컬럼 타입별 매핑 표 주석

- [ ] **Step 4: README 개선**
  - 컬럼 타입 매핑 표 (Kotlin → SQL) 추가
  - Index 전략 설명 추가
  - Sequence 사용 예제 강화

- [ ] **Step 5: 커밋**
  ```bash
  git add 04-exposed-r2dbc-ddl/02-ddl/
  git commit -m "docs: 04-ddl/02-ddl KDoc 및 README 보강"
  ```

---

## Chunk 2: 05-dml (01-dml, 02-types, 03-functions, 04-transactions)

### Task 4: 05-dml/01-dml

**Files:** `Ex01_Select.kt` ~ `Ex99_Dual.kt` + `README.md`

- [ ] **Step 1: 모든 파일 읽기**
  ```bash
  fd -e kt -t f . 05-exposed-r2dbc-dml/01-dml/src/test/ | sort
  ```

- [ ] **Step 2: 코드 이슈 수정**
  - `runBlocking` → `runTest` 대체 여부 확인
  - `.toList()` 후 단건 접근 → `.single()` 교체 검토
  - 과도한 중첩 `withTables` 정리

- [ ] **Step 3: KDoc 누락 메서드 보강**
  - SQL 예제 없는 메서드에 `-- Postgres` 기준 SQL 추가
  - UNION / EXCEPT / INTERSECT 예제 SQL 상세화
  - CTE(Recursive CTE) 예제 설명 강화

- [ ] **Step 4: README 개선**
  - 예제 테이블(Cities, Users, Sales) ER 다이어그램 텍스트 버전 추가
  - Flow 수집 패턴 예제 코드블록 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 05-exposed-r2dbc-dml/01-dml/
  git commit -m "docs: 05-dml/01-dml KDoc 및 README 보강"
  ```

---

### Task 5: 05-dml/02-types

- [ ] **Step 1: 파일 읽기**
  ```bash
  fd -e kt -t f . 05-exposed-r2dbc-dml/02-types/src/test/ | xargs cat
  ```

- [ ] **Step 2: 코드 이슈 수정**
  - 타입 매핑 일관성 검토 (nullable vs non-null 컬럼 처리)

- [ ] **Step 3: KDoc 보강**
  - 각 타입별 DB별 매핑 차이 설명 (H2 vs PostgreSQL vs MySQL)

- [ ] **Step 4: README 개선**
  - 지원 타입 매핑 표 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 05-exposed-r2dbc-dml/02-types/
  git commit -m "docs: 05-dml/02-types KDoc 및 README 보강"
  ```

---

### Task 6: 05-dml/03-functions

- [ ] **Step 1: 파일 읽기 및 분석**

- [ ] **Step 2: KDoc 보강**
  - 각 함수 예제에 실행 결과 예시 추가

- [ ] **Step 3: README 개선**
  - 지원 함수 카테고리별 표 추가 (문자열, 날짜, 집계, 수학)

- [ ] **Step 4: 커밋**
  ```bash
  git add 05-exposed-r2dbc-dml/03-functions/
  git commit -m "docs: 05-dml/03-functions KDoc 및 README 보강"
  ```

---

### Task 7: 05-dml/04-transactions

- [ ] **Step 1: 파일 읽기 및 분석**

- [ ] **Step 2: 코드 이슈 수정**
  - Rollback 테스트에서 `assertFailAndRollback` 일관 사용 확인
  - Savepoint 관련 DB 호환성 어노테이션 확인

- [ ] **Step 3: KDoc 보강**
  - 트랜잭션 격리 수준 설명 추가
  - Suspend 트랜잭션 vs 일반 트랜잭션 차이 설명

- [ ] **Step 4: README 개선**
  - 트랜잭션 패턴 다이어그램 (텍스트) 추가
  - Savepoint 지원 DB 표 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 05-exposed-r2dbc-dml/04-transactions/
  git commit -m "docs: 05-dml/04-transactions KDoc 및 README 보강"
  ```

---

## Chunk 3: 06-advanced (01~06)

### Task 8: 06-advanced/01-exposed-r2dbc-crypt

**Files:** `Ex01_EncryptedColumn.kt`, `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - 암호화 키 하드코딩 여부 확인 (테스트용 상수 명시 필요)

- [ ] **Step 3: KDoc 보강**
  - `encryptedVarchar`, `encryptedBinary` 사용 예제 상세화
  - 암호화/복호화 투명성 설명

- [ ] **Step 4: README 개선**
  - AES vs 다른 암호화 방식 비교 표 추가
  - 보안 주의사항 섹션 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 06-advanced/01-exposed-r2dbc-crypt/
  git commit -m "docs: 06-advanced/01-crypt KDoc 및 README 보강"
  ```

---

### Task 9: 06-advanced/02-exposed-r2dbc-javatime

**Files:** `Ex01_JavaTime.kt`, `Ex02_Defaults.kt`, `Ex03_DateTimeLiteral.kt`, `Ex04_MiscTable.kt`, `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - UTC vs local timezone 처리 일관성 확인
  - `CurrentTimestamp` vs `CurrentDateTime` 사용 구분 명확화

- [ ] **Step 3: KDoc 보강**
  - DB별 날짜/시간 타입 매핑 설명 (timestamp, datetime, date, time)
  - timezone 관련 주의사항

- [ ] **Step 4: README 개선**
  - java.time 타입 매핑 표 추가 (LocalDate, LocalDateTime, Instant 등)
  - timezone 처리 가이드 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 06-advanced/02-exposed-r2dbc-javatime/
  git commit -m "docs: 06-advanced/02-javatime KDoc 및 README 보강"
  ```

---

### Task 10: 06-advanced/03-exposed-r2dbc-kotlin-datetime

**Files:** `Ex01_KotlinDateTime.kt`, `Ex02_Defaults.kt`, `Ex03_DateTimeLiteral.kt`, `KotlinDateTimeSupports.kt`, `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: KDoc 보강**
  - kotlinx.datetime vs java.time 비교 설명
  - Multiplatform 호환성 설명

- [ ] **Step 3: README 개선**
  - kotlinx.datetime 타입 매핑 표 추가

- [ ] **Step 4: 커밋**
  ```bash
  git add 06-advanced/03-exposed-r2dbc-kotlin-datetime/
  git commit -m "docs: 06-advanced/03-kotlin-datetime KDoc 및 README 보강"
  ```

---

### Task 11: 06-advanced/04-exposed-r2dbc-json

**Files:** `Ex01_JsonColumn.kt`, `Ex02_JsonBColumn.kt`, `JsonTestData.kt`, `R2dbcExposedJsonTest.kt`, `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - JSON 경로 쿼리 (`jsonContains`, `jsonExtract`) 사용 패턴 검토
  - null JSON 처리 방식 확인

- [ ] **Step 3: KDoc 보강**
  - JSON vs JSONB 차이 설명
  - DB별 JSON 지원 현황 (H2 제한, PostgreSQL 강점)

- [ ] **Step 4: README 개선**
  - JSON 경로 쿼리 예제 추가
  - JSONB 인덱스 활용 설명

- [ ] **Step 5: 커밋**
  ```bash
  git add 06-advanced/04-exposed-r2dbc-json/
  git commit -m "docs: 06-advanced/04-json KDoc 및 README 보강"
  ```

---

### Task 12: 06-advanced/05-exposed-r2dbc-money

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - `MonetaryAmount` 비교 시 `compareTo` vs `==` 사용 확인

- [ ] **Step 3: KDoc + README 보강**
  - JSR-354 Money API 간략 설명
  - Currency 처리 주의사항

- [ ] **Step 4: 커밋**
  ```bash
  git add 06-advanced/05-exposed-r2dbc-money/
  git commit -m "docs: 06-advanced/05-money KDoc 및 README 보강"
  ```

---

### Task 13: 06-advanced/06-exposed-r2dbc-custom-columns

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - `ColumnType` 구현 패턴 확인
  - `valueFromDB` / `valueToDB` 대칭성 확인

- [ ] **Step 3: KDoc + README 보강**
  - 커스텀 컬럼 타입 구현 체크리스트 추가
  - 예시: UUID → String, Enum → Int 매핑 패턴

- [ ] **Step 4: 커밋**
  ```bash
  git add 06-advanced/06-exposed-r2dbc-custom-columns/
  git commit -m "docs: 06-advanced/06-custom-columns KDoc 및 README 보강"
  ```

---

## Chunk 4: 06-advanced (07~12)

### Task 14: 06-advanced/07-exposed-r2dbc-custom-entities

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - Entity ID 생성 전략 일관성 (UUID, Snowflake, TSID)

- [ ] **Step 3: KDoc + README 보강**
  - 커스텀 Entity ID 생성 전략 비교 표 추가

- [ ] **Step 4: 커밋**
  ```bash
  git add 06-advanced/07-exposed-r2dbc-custom-entities/
  git commit -m "docs: 06-advanced/07-custom-entities KDoc 및 README 보강"
  ```

---

### Task 15: 06-advanced/08-exposed-r2dbc-jackson

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: KDoc + README 보강**
  - Jackson ObjectMapper 설정 관련 주의사항
  - SerializationFeature 옵션 설명

- [ ] **Step 3: 커밋**
  ```bash
  git add 06-advanced/08-exposed-r2dbc-jackson/
  git commit -m "docs: 06-advanced/08-jackson KDoc 및 README 보강"
  ```

---

### Task 16: 06-advanced/09, 10, 11, 12 (fastjson2, jasypt, jackson3, tink)

- [ ] **Step 1: 각 파일 읽기**

- [ ] **Step 2: 공통 KDoc 보강**
  - 각 라이브러리의 특장점 설명 (Fastjson2: 성능, Jasypt: 결정적 암호화, Tink: Google 보안)

- [ ] **Step 3: README 개선**
  - 04-json / 08-jackson / 09-fastjson2 / 11-jackson3 비교 표 추가 (06-advanced/README.md)
  - 01-crypt / 10-jasypt / 12-tink 암호화 방식 비교 표 추가

- [ ] **Step 4: 커밋**
  ```bash
  git add 06-advanced/09-exposed-r2dbc-fastjson2/ 06-advanced/10-exposed-r2dbc-jasypt/ \
          06-advanced/11-exposed-r2dbc-jackson3/ 06-advanced/12-exposed-r2dbc-tink/ \
          06-advanced/README.md
  git commit -m "docs: 06-advanced 나머지 모듈 KDoc 및 README 보강"
  ```

---

## Chunk 5: 07-jpa-convert + 08-r2dbc-coroutines

### Task 17: 07-jpa-convert/01-convert-jpa-basic

**Files:** `ex01_simple/`, `ex02_entities/`, `ex03_customId/`, `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - JPA 어노테이션 스타일 코드가 남아있으면 Exposed 스타일로 완전 전환 확인
  - `@OneToMany` / `@ManyToOne` 패턴을 Exposed 관계로 설명하는 주석 추가

- [ ] **Step 3: KDoc 보강**
  - 각 ex 패키지 파일에 JPA → Exposed 전환 매핑 설명
  - Entity 관계 (1:N, N:M) 패턴 설명

- [ ] **Step 4: README 개선**
  - JPA 어노테이션 vs Exposed DSL 대응표 추가
  - 마이그레이션 체크리스트 추가

- [ ] **Step 5: 커밋**
  ```bash
  git add 07-jpa-convert/
  git commit -m "docs: 07-jpa-convert KDoc 및 README 보강"
  ```

---

### Task 18: 08-r2dbc-coroutines

**Files:**
- `01-exposed-r2dbc-coroutines-basic/Ex01_Coroutines.kt`
- `01-exposed-r2dbc-coroutines-basic/Ex02_CoroutinesFlow.kt`
- `02-exposed-r2dbc-virtualthreads-basic/Ex01_VritualThreads.kt` (오타 수정: Vritual → Virtual)
- 각 `README.md`

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - `Ex01_VritualThreads.kt` 파일명 오타 수정 여부 검토 (파일명 변경 시 import 영향)
  - `Dispatchers.IO` vs `Dispatchers.Unconfined` 선택 설명 추가

- [ ] **Step 3: KDoc 보강**
  - suspendTransaction vs transaction 차이
  - Flow 기반 스트리밍 쿼리 패턴

- [ ] **Step 4: README 개선**
  - Coroutine scope 관리 다이어그램 (텍스트)
  - Virtual Thread (JDK21) 활용 이점 설명

- [ ] **Step 5: 커밋**
  ```bash
  git add 08-r2dbc-coroutines/
  git commit -m "docs: 08-r2dbc-coroutines KDoc 및 README 보강"
  ```

---

## Chunk 6: 09-spring + 10-multi-tenant + 11-high-performance

### Task 19: 09-spring

- [ ] **Step 1: 파일 목록 파악**
  ```bash
  fd -e kt -t f . 09-spring | sort
  fd README.md 09-spring | sort
  ```

- [ ] **Step 2: 파일 읽기 및 코드 이슈 파악**

- [ ] **Step 3: KDoc 보강**
  - Repository 패턴 설명
  - Suspended Cache (Redis 기반) 작동 원리

- [ ] **Step 4: README 개선**
  - Spring DI + Exposed R2DBC 통합 패턴
  - Cache 전략 설명

- [ ] **Step 5: 커밋**
  ```bash
  git add 09-spring/
  git commit -m "docs: 09-spring KDoc 및 README 보강"
  ```

---

### Task 20: 10-multi-tenant

- [ ] **Step 1: 파일 목록 파악 및 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - Tenant 컨텍스트 전파 방식 (ThreadLocal vs CoroutineContext) 검토

- [ ] **Step 3: KDoc + README 보강**
  - Schema 기반 멀티테넌시 작동 원리 다이어그램 (텍스트)
  - Tenant 격리 수준 옵션 설명

- [ ] **Step 4: 커밋**
  ```bash
  git add 10-multi-tenant/
  git commit -m "docs: 10-multi-tenant KDoc 및 README 보강"
  ```

---

### Task 21: 11-high-performance

- [ ] **Step 1: 파일 목록 파악 및 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - 캐시 무효화 전략 검토
  - Read/Write 라우팅 로직 명확성

- [ ] **Step 3: KDoc + README 보강**
  - 캐시 전략 비교 (Local, Distributed)
  - Read/Write Routing 아키텍처 다이어그램 (텍스트)

- [ ] **Step 4: 커밋**
  ```bash
  git add 11-high-performance/
  git commit -m "docs: 11-high-performance KDoc 및 README 보강"
  ```

---

## Chunk 7: 루트 README 최종 보강

### Task 22: 루트 README.md 최종 업데이트

- [ ] **Step 1: 현재 루트 README 읽기**
  ```bash
  cat README.md
  ```

- [ ] **Step 2: 보강 내용 작성**
  - 빠른 시작 섹션 강화 (Docker 없이 H2로 첫 실행)
  - 모듈 맵 표에 예제 파일 수 추가
  - 아키텍처 다이어그램 (텍스트 기반) 추가
  - 기여 가이드 (새 예제 추가 방법) 추가
  - Exposed v1 주요 변경사항 요약

- [ ] **Step 3: 커밋**
  ```bash
  git add README.md
  git commit -m "docs: 루트 README 최종 보강"
  ```

---

## Chunk 8: 01-spring-boot + 03-exposed-r2dbc-basic

### Task 23: 01-spring-boot/spring-webflux-exposed

- [ ] **Step 1: 파일 목록 파악 및 읽기**

- [ ] **Step 2: 코드 이슈 수정**
  - WebFlux Controller 테스트 패턴 확인
  - Coroutine 예외 처리 (ControllerAdvice) 확인

- [ ] **Step 3: KDoc + README 보강**
  - Spring WebFlux + Exposed R2DBC 통합 흐름 설명
  - application.yml R2DBC 설정 예제

- [ ] **Step 4: 커밋**
  ```bash
  git add 01-spring-boot/
  git commit -m "docs: 01-spring-boot KDoc 및 README 보강"
  ```

---

### Task 24: 03-exposed-r2dbc-basic

- [ ] **Step 1: 파일 읽기**

- [ ] **Step 2: KDoc + README 보강**
  - SQL DSL 기본 패턴 설명 강화
  - JOIN 타입별 예제 (INNER, LEFT, CROSS) 명시

- [ ] **Step 3: 커밋**
  ```bash
  git add 03-exposed-r2dbc-basic/
  git commit -m "docs: 03-exposed-r2dbc-basic KDoc 및 README 보강"
  ```

---

## 실행 방법

병렬 subagent를 사용하여 청크를 동시 실행합니다:

```
Chunk 1 (00-shared, 04-ddl)        → subagent A
Chunk 2 (05-dml)                    → subagent B
Chunk 3 (06-advanced 01-06)        → subagent C
Chunk 4 (06-advanced 07-12)        → subagent D
Chunk 5 (07-jpa, 08-coroutines)    → subagent E
Chunk 6 (09-spring, 10, 11)        → subagent F
Chunk 7 (루트 README)               → main agent (모든 청크 완료 후)
Chunk 8 (01-spring-boot, 03-basic) → subagent G
```

각 subagent는 해당 청크의 task를 순서대로 수행하고 커밋합니다.
