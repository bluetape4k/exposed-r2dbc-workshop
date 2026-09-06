# Exposed 1.5 multi-row VALUES 검증 교훈

## 결정

Issue #231은 workshop 전체를 Exposed 1.5로 올리지 않고, `05-exposed-r2dbc-dml/01-dml` 테스트에만 JetBrains Exposed BOM `1.5.0`을 적용한다. Bluetape4k 안정 BOM `2.0.0`은 그대로 유지한다.

## 검증 범위

- `useMultiRowValues = true`: PostgreSQL 계열에서 하나의 `INSERT ... VALUES (...), (...)` 로그를 확인한다.
- `shouldReturnGeneratedValues = true`: 반환 행의 generated ID와 입력 행의 순서를 함께 확인한다.
- `shouldReturnGeneratedValues = false`: 반환 행에는 client-side 값만 남고 generated ID를 읽을 수 없음을 확인한다.
- `useMultiRowValues = false`: driver-level batch fallback이 행별 INSERT 로그를 남기는지 확인한다.
- 기존 `batch insert number of inserted rows`: 충돌을 무시한 실제 삽입 수를 검증하는 별도 custom executable 예제다.

## 제한 사항

`ignore = true`인 multi-row 부분 충돌은 driver의 per-entry update count 제공 여부에 의존한다. H2와 MariaDB는 이 count를 제공하지 않으므로 반환 행을 항상 실제 삽입 행과 동일하다고 해석하면 안 된다. 따라서 공용 seed는 기존처럼 `shouldReturnGeneratedValues = false`를 유지하고, PostgreSQL/MariaDB Testcontainers 매트릭스는 CI에서 확인한다.

## 재발 방지

JetBrains Exposed child artifact의 전역 버전 선언을 추가하지 않는다. 이 예제의 1.5 검증은 DML test configuration에만 적용하며, 다른 모듈과 `MovieSchema`의 generated-key workaround를 변경하지 않는다.
