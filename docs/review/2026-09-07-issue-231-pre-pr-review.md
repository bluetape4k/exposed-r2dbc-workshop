# Issue #231 사전 PR 리뷰

## 범위

Exposed `1.5.0` test-only BOM override와 `Ex02_Insert`의 multi-row VALUES,
driver-level batch fallback, generated-key 및 부분 충돌 반환 계약을 독립적으로
검토했다. `src/main` 변경은 없다.

## 리뷰 결과

- P0/P1: 없음.
- P2: 없음.
- 초기 P3: `kotlin.test.assertFailsWith` 사용으로 프로젝트 assertion 계약과
  어긋날 가능성이 있었으나 `io.bluetape4k.assertions.assertFailsWith`로 수정했다.
- 초기 P3: PostgreSQL만 가정하고 MariaDB 및 부분 충돌 경로를 실행하지 않았으나,
  세 multi-row 테스트의 지원 범위를 PostgreSQL/MariaDB로 넓히고
  `multi row insert ignore records actual table count`를 추가했다.

## 실행 증거

- `./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex02_Insert" -PuseDB=POSTGRESQL --console=plain`
  — `BUILD SUCCESSFUL`, 27개 통과.
- `./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex02_Insert" -PuseDB=MARIADB --console=plain`
  — `BUILD SUCCESSFUL`, 22개 통과/5개 skip.
- `./gradlew :01-dml:test -PuseDB=H2 --console=plain`
  — 재실행에서 `BUILD SUCCESSFUL`, 251개 통과/48개 skip.
- `git diff --check` — 통과.

MariaDB의 skip은 기존 dialect 비호환 예제 범위이며, 새 multi-row 및 부분 충돌
계약 테스트는 PostgreSQL/MariaDB에서 모두 통과했다. `:01-dml:detekt` task는
현재 Gradle project에 등록되어 있지 않아 실행할 수 없으며 targeted Kotlin
test/compilation 결과로 대체 증거를 남긴다.

## 잔여 게이트

PR 생성 후 exact-head CI, GitHub 리뷰 및 mergeability를 다시 확인해야 한다.
이 문서는 병합 승인을 의미하지 않는다.
