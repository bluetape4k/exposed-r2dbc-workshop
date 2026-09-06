# Issue #232: 공개 R2DBC 테스트 fixture 소비 설계

## 목표

workshop의 private `exposed.r2dbc.shared.tests` fixture를 이미 게시된
`io.github.bluetape4k.exposed:bluetape4k-exposed-r2dbc-tests:2.0.0`로 전환하는
첫 단계를 제공한다. production classpath에 Testcontainers/DB fixture를
추가하지 않고, 두 개의 순수 테스트 소비자에서 공개 API 호환성과 cleanup
계약을 증명한다.

## 현재 근거

- 기준 commit: `4a713b1b52f2534d7266ef13c85d6a4fb48e4242`
- 공개 API package: `io.bluetape4k.exposed.r2dbc.tests`
- 공개 base class: `AbstractExposedR2dbcTest`
- private 대응 API: `AbstractR2dbcExposedTest`, `TestDB`, `withDb`, `withTables`
- 선택 소비자:
  - `03-exposed-r2dbc-basic/exposed-r2dbc-sql-example`
  - `04-exposed-r2dbc-ddl/01-connection`
- 두 모듈은 private fixture를 `testImplementation`으로만 사용한다. DDL
  connection 모듈은 `CountryTable`/`DMLTestData`라는 shared production fixture도
  사용하므로 그 project dependency 자체는 유지한다.
- `00-shared/exposed-r2dbc-shared`는 production config/schema와 fixture를
  `src/main`에 함께 두므로 repository 전체 이동은 별도 작업이다.

## 선택지

1. **모든 module을 한 번에 public fixture로 이동**
   - 최종 상태에는 가깝지만 private API 사용처가 많고 shared production
     dependency를 test scope로 바꿀 수 없는 모듈이 섞여 위험하다.
2. **순수 test-only 소비자 두 개를 먼저 전환** (선택)
   - 공개 API의 signature, H2 lifecycle, test-only dependency 경계를 빠르게
     증명하고 후속 migration을 안전하게 분리한다.
3. **private fixture와 public fixture를 장기간 병행**
   - 단기 compile은 쉽지만 공개 API 전환 완료 조건과 runtime leakage를
     검증하지 못한다.

이번 PR은 2번을 채택한다. 선택한 두 consumer에서 private fixture import/base
class를 제거하고 public artifact alias를 추가한다. DDL connection 모듈의 shared
project dependency는 production fixture 사용 때문에 test scope로 유지한다.

## API 호환 계약

- `exposed.r2dbc.shared.tests.TestDB` → `io.bluetape4k.exposed.r2dbc.tests.TestDB`
- `withDb`/`withTables`의 suspend receiver와 기본 cleanup 옵션은 유지한다.
- `AbstractR2dbcExposedTest` 상속은 `AbstractExposedR2dbcTest`로 바꾼다.
- private에만 있는 `H2_ORACLE`/`H2_SQLSERVER`를 새 consumer에서 사용하지
  않는다.
- 기존 `-PuseFastDB=true` 설정과 공개 fixture의 `EXPOSED_TEST_DB` 차이는 각
  consumer test task에서 `EXPOSED_TEST_DB=H2`로 변환해 compatibility를 유지한다.
  그 외 CI 환경 변수는 그대로 통과시킨다.

## 변경 범위

- `gradle/libs.versions.toml`: public `bluetape4k-exposed-r2dbc-tests` alias를
  stable BOM 정책에 맞는 versionless child alias로 추가한다.
- 두 consumer `build.gradle.kts`: public test dependency를 추가한다. SQL example은
  private shared dependency를 제거하고, DDL connection은 shared production
  fixture 때문에 기존 test dependency를 유지한다.
- 두 consumer의 test imports/base class: package/class를 공개 API로 바꾼다.
- 각 consumer README 또는 lesson: public fixture와 test-only scope를 설명한다.
- migration lesson: shared module 전체 추출이 별도 후속 범위임을 명시한다.

## 비목표

- `00-shared`의 production/test source split
- private fixture를 즉시 삭제하거나 모든 module을 일괄 변경
- provider 저장소 API 변경 및 release
- DB dialect matrix를 public fixture에 맞춰 재설계

## 검증 기준

- [완료] 두 module의 runtime dependency graph에 public fixture가 들어가지 않고
  `testCompileClasspath`/`testRuntimeClasspath`에만 존재한다.
- [완료] H2 targeted tests에서 schema create/drop, transaction cleanup, cancellation
  경계를 통과한다.
- [완료] private fixture import가 두 consumer test source에서 사라진다.
- [완료] `git diff --check`, targeted tests, dependency insight를 통과한다.
  모듈별 static analysis task는 현재 Gradle project에 등록되어 있지 않다.
- [완료] `-PuseFastDB=true`에서 public fixture가 H2만 선택한다.
- [CI 대기] Testcontainers dialect matrix는 PR CI에서 확인한다.
