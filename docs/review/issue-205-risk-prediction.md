# Issue #205 위험 예측과 재실행 계약

검토일: 2026-08-28

| 위험 | 조기 신호 | 완화·검증 | 재실행 |
|---|---|---|---|
| provider API drift | catalog가 artifact를 해석하지 못하거나 generic signature compile error | versionless catalog alias, resolved 1.12.1 API, compileKotlin/compileTestKotlin | `./gradlew :09-checkpointable-r2dbc-batch:compileKotlin :09-checkpointable-r2dbc-batch:compileTestKotlin --no-daemon --console=plain` |
| metadata package 오용 | `batch.r2dbc.tables` import 또는 provider workaround 등장 | published `io.bluetape4k.batch.jdbc.tables.*`만 import, README에 경계 명시 | source import와 dependencyInsight 재검토 |
| chunk/checkpoint drift | 1.12.1 FAILED report가 checkpoint를 잃고 metadata를 null로 갱신 | STOPPED restart는 검증하되 FAILED restart는 provider release 전 차단; target `sourceId`는 중복을 관측할 뿐 멱등성을 보장하지 않음 | provider release 후 failed-run restart 회귀 테스트 |
| skip/retry/timeout 의미 변화 | attempt·skip count와 status가 기대와 다름 | provider DSL policy를 직접 설정하고 bounded 1ms/5ms values 사용 | named test 재실행 |
| cancellation leak | `CancellationException` 미전파, metadata가 STOPPED가 아님 | blocking writer, `STOPPED` metadata, restart no-duplicate assertion | cancellation test 재실행 |
| H2 profile 충돌 | `MODE=PostgreSQL`에서 metadata DDL 실패 | regular H2 URL로 고정; 이 모듈은 external DB compatibility를 주장하지 않음 | H2 test와 schema test 재실행 |
| docs/diagram drift | README snippet compile 실패, locale pair topology 불일치, PNG clipping | source-equivalent EN/KO read-back, semantic/geometry/endpoint/visual audit | 해당 문서/asset audit부터 재실행 |
| workflow 누락 | path filter·task·artifact 중 하나가 새 leaf를 건너뜀 | Examples, dynamic mapping, Nightly H2, `projects`를 함께 대조 | YAML/actionlint 및 changed-task script 재실행 |

## 중지 조건

P0/P1 finding, fresh module test 실패, provider API 불일치, cancellation
재전파 실패, 또는 credential/SQL 노출이 발견되면 PR을 만들지 않고 해당
slice를 재실행한다. 현재는 published provider release boundary에 P1이 있어
PR을 만들지 않는다.
