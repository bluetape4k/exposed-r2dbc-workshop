# 01-dml

Exposed R2DBC DSL을 사용한 **DML(Data Manipulation Language)
** 작업의 종합 예제 모듈입니다. SELECT, INSERT, UPDATE, DELETE, UPSERT, MERGE, JOIN, UNION, CTE 등 거의 모든 SQL DML 패턴을 27개의 테스트 파일로 다루고 있습니다.

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 프로젝트 구조

```
src/test/kotlin/exposed/r2dbc/examples/dml/
├── Ex01_Select.kt              # SELECT 기본: where, and/or, inList, inSubQuery, anyFrom, allFrom, distinct, limit/offset
├── Ex02_Insert.kt              # INSERT 기본: insert, batchInsert, insertIgnore, insertAndGetId
├── Ex03_Update.kt              # UPDATE 기본: update, joinQuery update, alias update
├── Ex04_Upsert.kt              # UPSERT: PK/Unique 충돌 시 insert-or-update, batchUpsert, onUpdate, where 조건
├── Ex05_Delete.kt              # DELETE: deleteWhere, deleteAll, deleteIgnoreWhere, join 기반 삭제
├── Ex06_Exists.kt              # EXISTS / NOT EXISTS 서브쿼리 조건
├── Ex07_DistinctOn.kt          # DISTINCT ON 절 (PostgreSQL 등)
├── Ex08_Count.kt               # COUNT, COUNT DISTINCT, 스키마별 count
├── Ex09_GroupBy.kt             # GROUP BY + 집계 함수 (sum, avg, min, max, having)
├── Ex10_OrderBy.kt             # ORDER BY: 단일/복합 정렬, SortOrder, nullsFirst/Last
├── Ex11_Join.kt                # JOIN: inner, left, cross, alias join, nested join, many-to-many
├── Ex12_InsertInto_Select.kt   # INSERT INTO ... SELECT 패턴
├── Ex13_Replace.kt             # REPLACE 문 (MySQL/MariaDB)
├── Ex14_MergeBase.kt           # MERGE 공통 베이스 클래스 (테이블 정의 및 테스트 데이터)
├── Ex14_MergeSelect.kt         # MERGE ... USING SELECT 패턴
├── Ex14_MergeTable.kt          # MERGE ... USING TABLE 패턴
├── Ex15_Returning.kt           # RETURNING 절 (INSERT/UPDATE/DELETE 결과 반환)
├── Ex16_FetchBatchedResults.kt # 대용량 결과 배치 조회 (fetchBatchedResults)
├── Ex17_Union.kt               # UNION / INTERSECT / EXCEPT 집합 연산
├── Ex20_AdjustQuery.kt         # adjustSelect, adjustWhere, adjustColumn 등 쿼리 동적 수정
├── Ex21_Arithmetic.kt          # 산술 연산 (plus, minus, times, div, rem)
├── Ex22_ColumnWithTransform.kt # 컬럼 값 변환 (transform을 이용한 직렬화/역직렬화)
├── Ex23_Conditions.kt          # 복합 조건절: compoundAnd, compoundOr, case/when, coalesce
├── Ex30_Explain.kt             # EXPLAIN / EXPLAIN ANALYZE 실행 계획 조회
├── Ex40_LateralJoin.kt         # LATERAL JOIN (PostgreSQL)
├── Ex50_RecursiveCTE.kt        # 재귀 CTE (WITH RECURSIVE)
└── Ex99_Dual.kt                # DUAL 테이블 (테이블 없는 SELECT)
```

> **참고**: 이 모듈은 `src/main`이 없고, 모든 코드가 `src/test`에 위치합니다. 학습/실습 목적의 테스트 전용 모듈입니다.

## 예제 카테고리

### 기본 CRUD

| 파일            | 설명                                                                                                           |
|---------------|--------------------------------------------------------------------------------------------------------------|
| `Ex01_Select` | WHERE 조건, AND/OR 결합, `inList`, `inSubQuery`, `anyFrom`, `allFrom`, DISTINCT, LIMIT/OFFSET 등 SELECT의 거의 모든 패턴 |
| `Ex02_Insert` | 단건/대량 INSERT, `insertIgnore`, `insertAndGetId`, auto-increment, client-default 값                             |
| `Ex03_Update` | 단건 UPDATE, joinQuery를 이용한 조건부 UPDATE, alias 기반 UPDATE                                                        |
| `Ex04_Upsert` | PK/Unique 충돌 시 INSERT or UPDATE, `batchUpsert`, `onUpdate` 커스텀 로직, `where` 조건, `onUpdateExclude`             |
| `Ex05_Delete` | `deleteWhere`, `deleteAll`, `deleteIgnoreWhere`, JOIN 기반 삭제                                                  |

### 집계 / 정렬 / 필터

| 파일                | 설명                                                  |
|-------------------|-----------------------------------------------------|
| `Ex06_Exists`     | EXISTS / NOT EXISTS 서브쿼리를 조건절에 활용                   |
| `Ex07_DistinctOn` | `DISTINCT ON` 절로 그룹 내 첫 행만 조회 (PostgreSQL)          |
| `Ex08_Count`      | `count()`, `countDistinct()`, 스키마 전환 후 count        |
| `Ex09_GroupBy`    | `GROUP BY` + `sum`, `avg`, `min`, `max`, `having` 절 |
| `Ex10_OrderBy`    | 단일/복합 컬럼 정렬, `SortOrder`, `nullsFirst`/`nullsLast`  |

### 조인

| 파일                 | 설명                                                                   |
|--------------------|----------------------------------------------------------------------|
| `Ex11_Join`        | INNER/LEFT/CROSS JOIN, alias join, nested join, many-to-many, 추가 조건절 |
| `Ex40_LateralJoin` | PostgreSQL LATERAL JOIN으로 상관 서브쿼리를 조인처럼 활용                           |

### 고급 DML

| 파일                            | 설명                                                                     |
|-------------------------------|------------------------------------------------------------------------|
| `Ex12_InsertInto_Select`      | `INSERT INTO ... SELECT` 패턴으로 다른 테이블 데이터 복사                            |
| `Ex13_Replace`                | MySQL/MariaDB `REPLACE` 문 (DELETE + INSERT)                            |
| `Ex14_MergeBase/Select/Table` | SQL `MERGE` 문: USING SELECT / USING TABLE 패턴, WHEN MATCHED/NOT MATCHED |
| `Ex15_Returning`              | INSERT/UPDATE/DELETE 후 `RETURNING` 절로 결과 즉시 반환 (PostgreSQL)            |

### 집합 연산

| 파일           | 설명                                                |
|--------------|---------------------------------------------------|
| `Ex17_Union` | `UNION`, `UNION ALL`, `INTERSECT`, `EXCEPT` 집합 연산 |

### 유틸리티 / 표현식

| 파일                         | 설명                                                             |
|----------------------------|----------------------------------------------------------------|
| `Ex20_AdjustQuery`         | `adjustSelect`, `adjustWhere`, `adjustColumn` 등으로 쿼리 동적 수정     |
| `Ex21_Arithmetic`          | 컬럼 간 산술 연산 (`+`, `-`, `*`, `/`, `%`)                           |
| `Ex22_ColumnWithTransform` | `transform()`을 이용한 컬럼 값 직렬화/역직렬화                               |
| `Ex23_Conditions`          | `compoundAnd`, `compoundOr`, `case`/`when`, `coalesce` 등 복합 조건 |

### 분석 / CTE / 기타

| 파일                         | 설명                                        |
|----------------------------|-------------------------------------------|
| `Ex30_Explain`             | `EXPLAIN` / `EXPLAIN ANALYZE`로 실행 계획 확인   |
| `Ex50_RecursiveCTE`        | `WITH RECURSIVE`를 이용한 재귀 CTE (계층 구조 조회)   |
| `Ex16_FetchBatchedResults` | 대용량 결과를 배치 단위로 조회 (`fetchBatchedResults`) |
| `Ex99_Dual`                | 테이블 없이 SELECT 실행 (DUAL 테이블 패턴)            |

## 핵심 코드 예제

### SELECT - 다양한 조건절

```kotlin
// WHERE + AND 조건 결합
users.selectAll()
    .where { users.id eq "andrey" }
    .andWhere { users.name.isNotNull() }
    .single()

// inList로 복수 값 필터링
users.selectAll()
    .where { users.id inList listOf("andrey", "alex") }
    .orderBy(users.name)
    .toFastList()

// inSubQuery로 서브쿼리 활용
val subQuery = cities.select(cities.id).where { cities.id eq 2 }
cities.selectAll()
    .where { cities.id inSubQuery subQuery }
```

### UPSERT - 충돌 시 INSERT or UPDATE

```kotlin
// PK 충돌 시 자동 UPDATE
AutoIncTable.upsert {
    it[id] = existingId
    it[name] = "Updated Name"
}

// onUpdate로 커스텀 UPDATE 로직 지정
Words.upsert(onUpdate = { it[Words.count] = Words.count + 1 }) {
    it[word] = testWord
}

// batchUpsert로 대량 처리
Words.batchUpsert(
    lettersWithDuplicates,
    onUpdate = { it[Words.count] = Words.count + 1 }
) { letter ->
    this[Words.word] = letter
}
```

### JOIN - 다양한 조인 패턴

```kotlin
// INNER JOIN (FK 기반 자동 조인)
users.innerJoin(cities)
    .select(users.name, cities.name)
    .where { cities.name eq "St. Petersburg" }
    .single()

// 3중 INNER JOIN
cities.innerJoin(users).innerJoin(userData)
    .selectAll()
    .orderBy(users.id)
    .toFastList()

// CROSS JOIN
cities.crossJoin(users)
    .select(users.name, cities.name)
    .where { cities.name eq "St. Petersburg" }
    .toFastList()
```

## 공유 테스트 인프라

이 모듈은 `00-shared/exposed-r2dbc-shared`의 공통 테스트 인프라를 사용합니다.

### DMLTestData

테스트 데이터를 제공하는 공유 객체입니다:

| 테이블           | 설명                                                 |
|---------------|----------------------------------------------------|
| `Cities`      | 도시 테이블 (id, name) - St. Petersburg, Munich, Prague |
| `Users`       | 사용자 테이블 (id, name, cityId, flags) - FK로 Cities 참조  |
| `UserData`    | 사용자 추가 정보 (userId, comment, value) - FK로 Users 참조  |
| `Sales`       | 매출 테이블 (year, month, product, amount)              |
| `SomeAmounts` | 금액 테이블 (amount) - inTable, anyFrom 등에 사용           |

주요 헬퍼 함수:

- `withCitiesAndUsers()` - Cities, Users, UserData를 생성하고 테스트 데이터 삽입
- `withSales()` - Sales 테이블에 tea/coffee 매출 데이터 삽입
- `withSalesAndSomeAmounts()` - Sales + SomeAmounts 테이블 동시 설정

### R2dbcExposedTestBase

모든 테스트 클래스의 베이스 클래스로, `ENABLE_DIALECTS_METHOD`를 통해 H2, MariaDB, MySQL, PostgreSQL 등 멀티 DB에서 동일 테스트를 실행합니다.

## 테스트 실행

```bash
# 전체 DML 테스트 실행
./gradlew :05-exposed-r2dbc-dml:01-dml:test

# 특정 테스트 클래스 실행
./gradlew :05-exposed-r2dbc-dml:01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"

# 특정 테스트 메서드 실행
./gradlew :05-exposed-r2dbc-dml:01-dml:test --tests "exposed.r2dbc.examples.dml.Ex04_Upsert.upsert with PK conflict"
```

## Further Reading

- [7.1 DML 함수](https://debop.notion.site/1ad2744526b0800baf1ce81c31f3cbf9?v=1ad2744526b08007ab62000c0901bcfa)
