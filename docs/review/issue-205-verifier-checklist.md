# Issue #205 Step 5 검증 체크리스트

검증일: 2026-08-28
기준: `docs/superpowers/specs/2026-08-28-issue-205-checkpointable-r2dbc-batch-design.md`,
`docs/superpowers/plans/2026-08-28-issue-205-checkpointable-r2dbc-batch-plan.md`,
현재 `develop` 기준 staged diff

| ID | 확인 내용 | 근거 | 상태 |
|---|---|---|---|
| A-VER-01 | provider-native API와 R2DBC transaction 경계 | `R2dbcBatchWorkshop.kt`, published 1.12.1 imports, compileKotlin/compileTestKotlin | PASS |
| A-VER-02 | schema, reader/processor/writer, job DSL, 8개 행동 test | module source/test와 `:09-checkpointable-r2dbc-batch:test --rerun-tasks` | PASS |
| A-VER-03 | 실패/skip/retry/timeout/cancellation/restart | `R2dbcBatchWorkshopTest.kt`의 8개 test, Chapter 13 smoke; FAILED restart는 provider blocker | BLOCKED |
| A-VER-04 | Gradle/catalog/module discovery | `libs.versions.toml`, module build, `./gradlew projects` | PASS |
| A-VER-05 | README/KDoc/diagram locale와 link | EN/KO heading·image parity, 128 local links, Korean terminology audit, diagram audits | PASS |
| A-VER-06 | Examples/Nightly/changed-task coverage | `Examples.yml`, changed-task script output, `actionlint`, YAML parse | PASS |
| A-VER-07 | scope와 known gap 공개 | review/lesson 문서의 H2 profile, root timeout, detekt/asset strict N/A | PASS |
| A-VER-08 | unrelated diff·temporary artifact 제거 | staged `git diff --stat`, temporary workflow JSON 없음, `git diff --cached --check` | PASS |
| A-VER-09 | 계획 artifact 이름과 실제 review/verification index 정렬 | `issue-205-implementation-review.md`, `issue-205-verification.md`가 상세 artifact로 연결되고, 계획의 단일 ledger 표기는 architecture/lifecycle kind별 두 ledger로 구체화됨 | PASS |

## Fresh validation

- `./gradlew :09-checkpointable-r2dbc-batch:test -PuseDB=H2 --rerun-tasks --no-daemon --console=plain`: 8/8 PASS
- Chapter 13 six-module H2 smoke with `--rerun-tasks --continue`: 28 tests PASS, `BUILD SUCCESSFUL`
- `./gradlew :09-checkpointable-r2dbc-batch:build -PuseDB=H2`: `BUILD SUCCESSFUL`, Kover verify 포함
- `./gradlew detekt --parallel --rerun-tasks`: `NO-SOURCE`, `BUILD SUCCESSFUL`; module-specific task는 N/A
- `actionlint .github/workflows/Examples.yml`: PASS; Ruby YAML parse `yaml-ok`
- diagram semantic/geometry/endpoint/arrowhead/connector/text/visual 및 module README link audit: PASS

## Known gaps와 영향

- provider 1.12.1의 writer commit과 checkpoint 저장은 별도 transaction이며,
  일반 `FAILED` report가 checkpoint를 보존하지 않는다. 따라서 failed-run
  restart는 P1 blocker다. retry backoff 실제 지연, timeout 중 transaction
  partial-write rollback, 명시적 pool dispose는 후속 P2 검증 항목이다.
- baseline root `./gradlew test`는 300초 timeout으로 종료되어 root full suite는 미확인이다. 변경 module와 Chapter 13 smoke는 통과했으며 PR DoD에 validation gap으로 표시한다.
- 이 module에 별도 `detekt` task가 없고, shared asset directory 전체의 strict README exposure는 기존 공용 자산 때문에 module 증거가 아니다. 각각 N/A로 기록한다.
- `kotlin-language-server` binary는 있으나 이 환경에 `lsp_diagnostics` client가 없어 LSP 진단은 실행하지 못했다. compile/test와 detekt aggregate evidence로 대체하고 gap을 공개한다.

## Verdict

컴파일·H2·workflow·문서 자산 검증은 통과했지만, published `1.12.1`에
#747 FAILED checkpoint 보존 수정이 없어 Issue #205의 FAILED 후 restart
수용 기준은 검증할 수 없다. P0=0, P1=1이며 Step 5 verdict는 `BLOCKED`다.

provider backport/release 또는 승인된 scope 변경 뒤 FAILED restart 회귀
테스트를 추가하고 이 체크리스트를 다시 실행해야 한다.
