# Issue #205 checkpointable Exposed R2DBC batch 교훈

## 결정

JDBC sibling의 학습 목표를 R2DBC에 옮길 때 blocking runner를 감싸지 않고,
published `bluetape4k-exposed-batch:1.12.1`의
`ExposedR2dbcBatchJobRepository`, `ExposedR2dbcBatchReader`,
`ExposedR2dbcBatchWriter`를 provider DSL에서 직접 조합했다. Metadata table은
현재 published artifact의 `io.bluetape4k.batch.jdbc.tables` 경계를 따르며,
향후 unreleased package를 선제적으로 복제하지 않는다.

## 재사용할 패턴

1. `R2dbcDatabase`는 caller가 소유하고 schema/fixture/query는
   `suspendTransaction` 안에 둔다.
2. keyset reader는 마지막으로 commit된 `Long` key를 checkpoint로 저장하고,
   target `sourceId` primary key로 restart 중복을 눈에 보이게 한다. 다만
   published `1.12.1`은 일반 `FAILED` report에서 checkpoint를 보존하지
   않으므로 현재 검증 범위는 `STOPPED` restart다.
3. cancellation은 catch-all 예외로 바꾸지 않고 `STOPPED` metadata를 남긴 뒤
   caller coroutine으로 재전파한다.
4. EN/KO README는 source-equivalent로 유지하되 API names, paths, commands와
   exact status token은 보존한다. diagram은 semantic ledger에서 topology를
   먼저 고정하고 SVG·PNG pair를 함께 감사한다.

## 발견한 경계

- H2 `MODE=PostgreSQL`은 provider metadata DDL의 `BIGINT AUTO_INCREMENT`와
  충돌했다. regular H2 URL로 전환했으며 external DB compatibility를 이
  예제의 결과로 일반화하지 않는다.
- root full test는 baseline에서 300초 안에 끝나지 않았다. 변경 module build,
  8개 targeted H2 test, Chapter 13 six-module smoke, aggregate detekt로
  검증을 보강했지만 root full test timeout은 후속 관찰 항목이다.
- module-specific detekt task와 shared asset global strict exposure는 이
  repository/tool 범위에 없다. 없는 검사를 통과했다고 보고하지 않고 N/A로
  기록한다.
- upstream #747의 FAILED checkpoint 수정은 `[2.0.0]` merge로 들어갔지만
  Maven Central에는 아직 published되지 않았다. 1.12.1을 유지하면서
  workaround를 넣지 않는 승인 경계에서는 FAILED restart를 완료로
  주장하지 않고, provider release 또는 명시적 scope 결정까지 PR을 보류한다.

## 다음 작업에 적용할 점

- provider dependency를 추가할 때는 catalog alias, resolved JAR signature,
  source package를 한 번에 대조한다.
- 테스트가 green인 뒤에도 README code block을 실제 API 호출과 비교해
  compile-valid 여부를 확인한다.
- PR body는 issue/milestone/assignee/labels와 DoD evidence를 live read-back한
  뒤 작성하고, `## DoD Status`를 마지막 section으로 유지한다.
