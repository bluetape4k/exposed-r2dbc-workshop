# README UML 다이어그램 추가 설계

**날짜**: 2026-03-23
**작업**: 모든 서브모듈 README에 Mermaid UML 다이어그램 추가
**참조**: `~/work/bluetape4k/exposed-workshop` 서브모듈 README 패턴

---

## 목표

`exposed-r2dbc-workshop`의 25개 서브모듈 README에 Mermaid UML 다이어그램을 추가하여 학습자가 각 모듈의 구조와 동작 흐름을 직관적으로 이해할 수 있도록 한다.

## 현황

- 전체 README 파일: 32개
- 이미 Mermaid 다이어그램 있음: 7개
    - `README.md` (루트)
    - `01-spring-boot/spring-webflux-exposed/README.md`
    - `00-shared/exposed-r2dbc-shared/README.md`
    - `06-advanced/README.md`
    - `10-multi-tenant/03-multitenant-spring-webflux/README.md`
    - `09-spring/05-exposed-r2dbc-repository-coroutines/README.md`
    - `11-high-performance/03-routing-datasource/README.md`
- **다이어그램 추가 필요**: 25개

## 다이어그램 유형 기준

모듈 성격에 따라 가장 적합한 다이어그램 유형을 자동 선택한다 (복수 적용 가능):

| 유형                | 적용 기준                               |
|-------------------|-------------------------------------|
| `erDiagram`       | 테이블 간 관계(FK, PK)가 핵심인 모듈            |
| `classDiagram`    | 클래스/인터페이스/타입 계층 구조가 핵심인 모듈          |
| `sequenceDiagram` | 비동기 흐름, 트랜잭션, 캐시 조회 등 시간 순서가 중요한 모듈 |
| `flowchart`       | 분기 로직, 마이그레이션 경로, 카테고리 분류가 핵심인 모듈   |

## 처리 방식

**방식**: 모듈 그룹별 병렬 Writer 에이전트 디스패치
**순서**: 그룹 1 → 그룹 2 → 그룹 3 → 그룹 4 (순차 배치)

각 그룹 내에서는 에이전트를 병렬 실행하여 속도를 최적화한다.

---

## 그룹별 상세 계획

### 그룹 1 — `05-exposed-r2dbc-dml` (4개 파일)

| 파일                                               | 다이어그램                           | 내용                                                        |
|--------------------------------------------------|---------------------------------|-----------------------------------------------------------|
| `05-exposed-r2dbc-dml/01-dml/README.md`          | `erDiagram`                     | Cities → Users → UserData FK 관계, Sales, SomeAmounts       |
| `05-exposed-r2dbc-dml/02-types/README.md`        | `classDiagram`                  | Exposed 컬럼 타입 계층 (IntegerColumnType, VarCharColumnType 등) |
| `05-exposed-r2dbc-dml/03-functions/README.md`    | `flowchart`                     | 함수 카테고리: 문자열/수학/날짜/집계 분류                                  |
| `05-exposed-r2dbc-dml/04-transactions/README.md` | `sequenceDiagram` + `flowchart` | R2DBC suspendTransaction 흐름 + 중첩 트랜잭션/Savepoint 분기        |

### 그룹 2 — `04-exposed-r2dbc-ddl` + `03-exposed-r2dbc-basic` (3개 파일)

| 파일                                                           | 다이어그램                        | 내용                                         |
|--------------------------------------------------------------|------------------------------|--------------------------------------------|
| `04-exposed-r2dbc-ddl/01-connection/README.md`               | `sequenceDiagram`            | R2DBC ConnectionFactory → Database 연결 생명주기 |
| `04-exposed-r2dbc-ddl/02-ddl/README.md`                      | `classDiagram` + `flowchart` | Table DSL 클래스 구조 + create/alter/drop 흐름    |
| `03-exposed-r2dbc-basic/exposed-r2dbc-sql-example/README.md` | `erDiagram` + `classDiagram` | 기본 테이블 관계 + SQL DSL Query 구조               |

### 그룹 3 — `06-advanced` (12개 파일)

| 파일                                                       | 다이어그램                        | 내용                                                |
|----------------------------------------------------------|------------------------------|---------------------------------------------------|
| `06-advanced/01-exposed-r2dbc-crypt/README.md`           | `sequenceDiagram`            | 저장 시 암호화 → DB → 조회 시 복호화 흐름                       |
| `06-advanced/02-exposed-r2dbc-javatime/README.md`        | `classDiagram`               | Java Time 타입 ↔ Exposed 컬럼 타입 매핑                   |
| `06-advanced/03-exposed-r2dbc-kotlin-datetime/README.md` | `classDiagram`               | kotlinx-datetime 타입 ↔ Exposed 컬럼 타입 매핑            |
| `06-advanced/04-exposed-r2dbc-json/README.md`            | `classDiagram`               | JSON 컬럼 타입 계층 (json vs jsonb DB 호환성 차이 강조)        |
| `06-advanced/05-exposed-r2dbc-money/README.md`           | `classDiagram`               | Money/MonetaryAmount 컬럼 계층                        |
| `06-advanced/06-exposed-r2dbc-custom-columns/README.md`  | `classDiagram`               | Column → CustomColumn 확장 구조                       |
| `06-advanced/07-exposed-r2dbc-custom-entities/README.md` | `classDiagram` + `erDiagram` | Entity DAO 계층 + 테이블 관계                            |
| `06-advanced/08-exposed-r2dbc-jackson/README.md`         | `classDiagram`               | Jackson 기반 JSON 컬럼 구조 (ObjectMapper 통합 방식 강조)     |
| `06-advanced/09-exposed-r2dbc-fastjson2/README.md`       | `classDiagram`               | FastJson2 기반 JSON 컬럼 구조 (JSONReader/Writer 방식 강조) |
| `06-advanced/10-exposed-r2dbc-jasypt/README.md`          | `sequenceDiagram`            | Jasypt 암호화 컬럼 저장/조회 흐름                            |
| `06-advanced/11-exposed-r2dbc-jackson3/README.md`        | `classDiagram`               | Jackson3 기반 JSON 컬럼 구조 (Jackson2 대비 API 변경점 강조)   |
| `06-advanced/12-exposed-r2dbc-tink/README.md`            | `sequenceDiagram`            | Tink 기반 암호화 흐름                                    |

### 그룹 4 — `07~11` 나머지 (6개 파일)

| 파일                                                                    | 다이어그램                           | 내용                                                                       |
|-----------------------------------------------------------------------|---------------------------------|--------------------------------------------------------------------------|
| `07-jpa-convert/01-convert-jpa-basic/README.md`                       | `flowchart`                     | JPA Entity/Repository → Exposed Table/DSL 마이그레이션 경로                      |
| `08-r2dbc-coroutines/01-exposed-r2dbc-coroutines-basic/README.md`     | `sequenceDiagram` + `flowchart` | Coroutine Scope → suspendTransaction → Flow 수집 흐름                        |
| `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md` | `sequenceDiagram`               | `runSuspendVT` → `virtualThreadTransaction` → `suspendTransaction` 실행 흐름 |
| `09-spring/07-spring-suspended-cache/README.md`                       | `sequenceDiagram`               | Redis 캐시 조회 흐름 (cache hit/miss)                                          |
| `11-high-performance/README.md`                                       | `flowchart`                     | 고성능 전략 개요 (캐시 + 라우팅 DataSource 구조)                                       |
| `11-high-performance/02-cache-strategies-r2dbc/README.md`             | `classDiagram` + `flowchart`    | 캐시 전략 클래스 구조 + 캐시 계층 흐름                                                  |

---

## 다이어그램 스타일 가이드

참조 프로젝트(`exposed-workshop`) 패턴을 따른다:

1. **언어**: 한국어 주석/레이블
2. **erDiagram**: PK/FK 명시, 관계 카디널리티 표기
3. **classDiagram**: `<<interface>>`, `<<enumeration>>` 스테레오타입 활용
4. **sequenceDiagram**: `alt/else` 블록으로 정상/오류 경로 구분
5. **flowchart**: `TD` (top-down) 방향 기본, 분기점은 다이아몬드 노드

## 품질 기준

- 기존 README 내용을 삭제하지 않고 적절한 위치(기술 스택 표 직후 또는 핵심 개념 섹션 앞)에 삽입
- 다이어그램은 실제 소스 코드 구조를 반영 (추측 금지)
- 코드 읽기 → 다이어그램 생성 순서 엄수
