# 공개 R2DBC 테스트 fixture 소비 전환 교훈

Issue #232에서 `03-exposed-r2dbc-basic/exposed-r2dbc-sql-example`와
`04-exposed-r2dbc-ddl/01-connection`의 테스트 fixture를
`io.github.bluetape4k.exposed:bluetape4k-exposed-r2dbc-tests:2.0.0`으로
전환했다.

## 확인한 계약

- `TestDB`, `withTables`, `AbstractExposedR2dbcTest`는 공개 package에서
  기존 테스트의 H2 lifecycle과 호환된다.
- public 의존성은 두 모듈의 `testImplementation`에만 두어 production runtime
  graph에 새 fixture artifact를 추가하지 않는다. DDL connection 모듈은
  `CountryTable`/`DMLTestData` 때문에 기존 shared project도 test scope로
  유지한다.
- 공개 fixture는 private fixture와 DB 선택 property/지원 dialect 목록이 다르다.
  이번 소비자는 기본 H2 경로를 사용하므로 차이를 숨기지 않고 후속 migration의
  compatibility 항목으로 남긴다.

## 범위 제한

`00-shared/exposed-r2dbc-shared`는 production config/schema와 private fixture를
같은 `src/main`에 둔다. 따라서 전체 repository의 fixture 삭제와 source-set
분리는 별도 계획이 필요하며 이번 PR에서 수행하지 않는다. SQL example처럼
shared production fixture가 필요 없는 소비자는 project dependency까지 제거하고,
DDL connection처럼 domain fixture가 필요한 소비자는 test scope로 유지하면서
public lifecycle API만 먼저 이동한다.

## 재사용 규칙

다음 소비자 migration에서도 public package import와 test-only dependency scope를
먼저 확인한다. private fixture가 production source에서 사용되는 module은 shared
source split과 별도 release evidence 없이 함께 바꾸지 않는다.
