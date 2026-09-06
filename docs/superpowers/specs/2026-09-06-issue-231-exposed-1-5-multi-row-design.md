# Issue #231: Exposed 1.5 multi-row VALUES 검증 설계

## 목표

안정 Bluetape BOM `2.0.0` 소비 정책은 유지하면서 DML workshop에서 JetBrains
Exposed `1.5.0`의 R2DBC `batchInsert(useMultiRowValues = true)` 경로를
실행 예제로 고정한다. 일반 driver batch와 단일 `INSERT ... VALUES (...),
(...)` 경로의 차이, 반환 행 계약, generated-key 제한을 테스트와 한국어
설명으로 남긴다.

## 현재 근거

- 기준 commit: `4a713b1b52f2534d7266ef13c85d6a4fb48e4242`
- 현재 workshop BOM: `io.github.bluetape4k:bluetape4k-dependencies:2.0.0`
- 현재 Exposed child 모듈은 BOM으로 `1.4.0`을 해결한다.
- 기준 예제: `05-exposed-r2dbc-dml/01-dml/.../Ex02_Insert.kt`
- 공용 seed는 generated values를 끄는 기존 workaround를 유지한다.
- Exposed `1.5.0` 공식 release는 R2DBC multi-row VALUES와 실제 삽입 행 반환
  수정을 포함한다: <https://github.com/JetBrains/Exposed/releases/tag/1.5.0>

## 선택지

1. **BOM을 `2.1.0-SNAPSHOT`으로 전환**
   - 중앙 버전 권위를 유지하지만 안정 소비자 정책과 전체 workshop 범위를
     바꾸고, 다른 모듈까지 snapshot 영향을 받는다.
2. **DML 테스트 모듈에만 Exposed `1.5.0` 플랫폼 override** (선택)
   - 변경 면적이 한 모듈의 테스트 classpath로 제한되고 안정 Bluetape BOM은
     그대로 둔다. 이번 issue가 명시한 예제/검증 override에 해당한다.
3. **중앙 stable release까지 대기**
   - 가장 보수적이나 현재 issue의 예제 검증을 완료하지 못한다.

이번 PR은 2번을 채택한다. override는 JetBrains Exposed BOM 하나로 묶고
Bluetape artifact version은 추가하지 않는다.

## 동작 계약

- `useMultiRowValues = true`는 지원 dialect에서 하나의 multi-row VALUES SQL을
  사용한다.
- `useMultiRowValues = false`는 기존 driver-level batch fallback이다.
- 일반 generated values 경로는 반환 결과 수와 실제 삽입 수를 확인한다.
- `shouldReturnGeneratedValues = false`는 generated-key가 필요 없는 seed와
  fallback에 유지한다.
- `ignore = true`인 부분 충돌 반환은 driver별 update count 차이가 있으므로
  “항상 삽입된 행만 반환”으로 문서화하지 않는다.
- 단일 SQL 검증은 H2 PostgreSQL mode에서 SQL logger로 수행하며, PostgreSQL
  Testcontainers는 CI/승인된 환경에서 별도 순차 검증한다.

## 변경 범위

- `gradle/libs.versions.toml`: 외부 `org.jetbrains.exposed:exposed-bom:1.5.0`
  test override alias만 추가한다.
- `05-exposed-r2dbc-dml/01-dml/build.gradle.kts`: 테스트 classpath에 Exposed
  BOM을 추가한다.
- `Ex02_Insert.kt`: multi-row VALUES, fallback, generated values, conflict
  반환 계약을 보여주는 테스트와 한국어 KDoc을 추가한다.
- 양쪽 DML README: API 선택과 driver별 반환 제한을 설명한다.
- `docs/lessons/...`: 기존 generated-key workaround를 유지하는 이유와 검증
  결과를 기록한다.

## 비목표

- Bluetape stable BOM release/publication 또는 중앙 catalog 수정
- `MovieSchema` seed의 generated-key 정책 변경
- Exposed provider 구현(#803/#805) 또는 다른 module의 batch API 전환
- 성능 우위 주장. statement 수와 측정 결과가 없는 경우 기록하지 않는다.

## 검증 기준

- [완료] Gradle resolved graph에서 Exposed core/r2dbc가 `1.5.0`, Bluetape BOM이
  `2.0.0`임을 확인한다.
- [완료] H2 대상 테스트에서 multi-row SQL shape, fallback row count, generated values,
  duplicate conflict 및 rollback을 확인한다.
- [CI 대기] PostgreSQL 및 MariaDB Testcontainers는 순차 실행하고 결과를 PR에 기록한다.
- [완료] `git diff --check`와 targeted test를 통과했다. 모듈별 static analysis
  task는 현재 Gradle project에 등록되어 있지 않다.

## 결정 기록

중앙 stable consumer 정책을 훼손하지 않으면서 issue가 요구한 Exposed 1.5
전용 API를 검증해야 하므로 테스트 classpath 한정 override가 최소 변경이다.
부분 충돌 반환은 upstream/driver 계약이 완전히 동일하지 않으므로 예제에서
보수적으로 제한한다.
