# Issue #115 Chapter 13 Foundation 교훈

## 맥락

Issue #115는 `exposed-workshop/13-ecosystem-integrations`를 바탕으로 첫 R2DBC Chapter 13 ecosystem example을 추가했다.

## 결정

이 R2DBC workshop chapter에는 CockroachDB retry handling만 유지한다. CockroachDB는 PostgreSQL-compatible R2DBC boundary를 통해 가르칠 수 있고, default test는 H2와 synthetic SQLSTATE `40001` retry failure로 local에 남길 수 있다.

BigQuery, Trino, StarRocks, DuckDB를 local R2DBC stand-in으로 유지하지 않는다. 이 example들은 workshop context에서 JDBC, HTTP/native-client, embedded-client 중심이므로, 유지하면 false parity가 생긴다.

## 방어선

향후 ecosystem example을 추가하기 전에, 대상 system이 가르치려는 lesson에 대해 실제 R2DBC-shaped driver 또는 protocol path를 갖는지 확인한다. Source example이 근본적으로 JDBC, HTTP/native client, embedded-client 기반이라면 fake local adapter module을 추가하지 말고 R2DBC scope 밖이라고 문서화한다.

## 검증

- `repo-test-summary -- ./gradlew :03-cockroachdb-retry:test -PuseDB=H2 --continue --console=plain`
