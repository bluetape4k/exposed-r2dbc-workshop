# Shared version drift 정리

## 맥락

`exposed-r2dbc-workshop`의 `bluetape4k-dependencies`와 JetBrains Exposed 버전이 중앙 `bluetape4k-dependencies` catalog보다 낮게 남아 있었다.

## 결정

Local catalog만 갱신해 `bluetape4k-dependencies=1.0.0`, `exposed=1.3.0`으로 맞춘다. `bluetape4k-dependencies:1.0.0`이 관리하는 published artifact 이름에 맞춰 `io.github.bluetape4k.exposed:bluetape4k-exposed-*` 좌표도 함께 정렬한다. 기존 artifact rename worktree와 섞지 않는다.

## 결과

중앙 source-of-truth 검증에서 R2DBC workshop repo가 shared version drift를 만들지 않도록 정리했다.

## 검증

- `../bluetape4k-dependencies/.worktrees/feat/dependency-governance/scripts/sync-shared-versions.py --workspace <symlink-workspace> --repo exposed-r2dbc-workshop --check --summary`
- `./gradlew compileKotlin compileTestKotlin --no-daemon`
- `./gradlew :01-exposed-r2dbc-crypt:test --no-daemon`
- `./gradlew build --no-daemon`은 처음에 `:01-exposed-r2dbc-crypt:test`의 일시적인 MySQL R2DBC connection loss로 실패했고, targeted retry는 통과했다.
- `git diff --check`

## 향후 방어선

Workshop repo도 library repo와 같은 shared version gate를 통과해야 한다. drift가 작아도 별도 PR로 기록하고 중앙 script 결과를 증거로 남긴다.
