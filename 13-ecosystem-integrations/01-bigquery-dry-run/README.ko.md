# BigQuery Dry-Run Boundary

[English](README.md) | [한국어](README.ko.md)

이 모듈은 local Exposed R2DBC query를 렌더링하고 typed BigQuery dry-run
request로 감쌉니다. 기본 테스트는 BigQuery client를 만들지 않으며 credential을
요구하지 않습니다.

## 시나리오

- R2DBC transaction 안에서 `BigQueryDryRunSource` SQL을 렌더링합니다.
- `dryRun = true`, `useLegacySql = false`인 `BigQueryDryRunRequest`를 만듭니다.
- Default dataset, location, label, priority, timeout을 명시합니다.

## 다이어그램

ERD는 local source table, adapter profile, typed request boundary를 보여줍니다.
Sequence Diagram은 credential-free 기본 테스트 흐름을 보여줍니다.

![BigQuery dry-run ERD](../../docs/assets/readme-diagrams/issue-115-chapter13-01-bigquery-dry-run-erd-01.png)

![BigQuery dry-run sequence](../../docs/assets/readme-diagrams/issue-115-chapter13-01-bigquery-dry-run-sequence-01.png)

## 검증

```bash
./gradlew :01-bigquery-dry-run:test -PuseDB=H2 --console=plain
```
