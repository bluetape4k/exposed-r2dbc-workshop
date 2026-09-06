# Issue #232 사전 PR 리뷰

## 범위

두 consumer의 private R2DBC test fixture 의존성을 공개
`bluetape4k-exposed-r2dbc-tests` artifact로 전환하고, test-only classpath와
기존 fast path 계약을 확인했다. production source와 runtime dependency에는
변경이 없다.

## 리뷰 결과

- P0/P1: 없음.
- 초기 HIGH: 공개 fixture가 읽는 `EXPOSED_TEST_DB`와 기존
  `-PuseFastDB=true`가 달라 fast path가 전체 DB matrix로 확장될 수 있었다.
  두 consumer의 Test task에 `-PuseFastDB=true` → `EXPOSED_TEST_DB=H2` adapter를
  추가하고 재검증했다.
- MEDIUM: public helper의 직접 cancellation/cleanup 계약을 새 테스트로 늘리지는
  않았다. 기존 helper suite와 consumer 회귀 테스트를 유지하고, artifact CI
  matrix를 PR 게이트로 남긴다.

## 실행 증거

- `EXPOSED_TEST_DB=H2 ./gradlew :exposed-r2dbc-sql-example:test` — 6개 통과.
- `EXPOSED_TEST_DB=H2 ./gradlew :01-connection:test` — 15개 통과.
- `./gradlew :exposed-r2dbc-sql-example:test :01-connection:test -PuseFastDB=true --rerun-tasks --console=plain`
  — `BUILD SUCCESSFUL`, H2 6개 + 15개 통과.
- 두 consumer의 `dependencyInsight`에서
  `io.github.bluetape4k.exposed:bluetape4k-exposed-r2dbc-tests:2.0.0`을 확인했다.
- public fixture는 runtimeClasspath에 들어가지 않고 test classpath에만 존재한다.
- `:exposed-r2dbc-sql-example:detekt`, `:01-connection:detekt` task는 현재
  Gradle project에 등록되어 있지 않다.
- `git diff --check` — 통과.

## 잔여 게이트

PR 생성 후 exact-head CI, GitHub 리뷰 및 mergeability를 다시 확인해야 한다.
이 문서는 병합 승인을 의미하지 않는다.
