# DuckDB Embedded Analytics Boundary

[English](README.md) | [한국어](README.ko.md)

이 모듈은 원본 workshop의 embedded analytics lesson을 유지하되 R2DBC boundary를
명확히 합니다. 기본 테스트는 DuckDB JDBC session을 열지 않고 H2 R2DBC local
projection을 사용합니다.

## 시나리오

- `DuckDbOrderEvents`에 order event를 저장합니다.
- Exposed R2DBC로 daily category sales를 projection합니다.
- Grouped analytics SQL을 렌더링합니다.
- 이 workshop에서 DuckDB를 JDBC 중심 embedded engine으로 보는 이유를 문서화합니다.

## 다이어그램

ERD는 local order event, DTO row, daily category sales, DuckDB boundary note를
보여줍니다. Sequence Diagram은 H2 R2DBC projection을 보여주면서 DuckDB path를
명시적인 non-default 경계로 유지합니다.

![DuckDB embedded analytics ERD](../../docs/assets/readme-diagrams/issue-115-chapter13-09-duckdb-embedded-analytics-erd-01.png)

![DuckDB embedded analytics sequence](../../docs/assets/readme-diagrams/issue-115-chapter13-09-duckdb-embedded-analytics-sequence-01.png)

## 검증

```bash
./gradlew :09-duckdb-embedded-analytics:test -PuseDB=H2 --console=plain
```
