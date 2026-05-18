# Shared version drift 정리

## Context

`exposed-r2dbc-workshop`는 `bluetape4k-dependencies`와 JetBrains Exposed 버전이 중앙 `bluetape4k-dependencies` catalog보다 낮게 남아 있었다.

## Decision

Local catalog만 갱신해 `bluetape4k-dependencies=1.0.0`, `exposed=1.3.0`으로 맞춘다. `bluetape4k-dependencies:1.0.0`이 관리하는 published artifact 이름에 맞춰 `io.github.bluetape4k.exposed:bluetape4k-exposed-*` 좌표도 함께 정렬한다. 기존 artifact rename worktree와 섞지 않는다.

## Outcome

중앙 source-of-truth 검증에서 R2DBC workshop repo가 shared version drift를 만들지 않도록 정리했다.

## Verification

- `../bluetape4k-dependencies/.worktrees/feat/dependency-governance/scripts/sync-shared-versions.py --workspace <symlink-workspace> --repo exposed-r2dbc-workshop --check --summary`
- `./gradlew compileKotlin compileTestKotlin --no-daemon`
- `./gradlew :01-exposed-r2dbc-crypt:test --no-daemon`
- `./gradlew build --no-daemon` failed first on transient MySQL R2DBC connection loss in `:01-exposed-r2dbc-crypt:test`; the targeted retry passed.
- `git diff --check`

## Future Guard

Workshop repos도 library repos와 같은 shared version gate를 통과해야 한다. drift가 작아도 별도 PR로 기록하고 중앙 script 결과를 증거로 남긴다.
