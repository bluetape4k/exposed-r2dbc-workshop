# WIP Audit와 withTables cancellation

## 맥락

2026-05-18 qmd-backed `exposed-r2dbc-workshop` audit는 이전 WIP refresh와 CTE Query Builder lesson, live GitHub issue 상태, shared helper code를 확인했다.

## 결정 또는 발견

`withTables()`는 shared R2DBC test infrastructure이며 현재 suspend cleanup을 `runCatching`과 넓은 `Throwable` 처리로 감싼다. 이 방식은 일반 cleanup diagnostics는 보존하지만 coroutine `CancellationException` propagation을 명시적으로 보호하지 않는다.

## 결과

GitHub issue #54를 등록하고, `WIP.md`를 할당된 이슈 0개에서 live assigned issue 19개로 갱신했다.

## 검증

- `qmd query ... --no-rerank -c bluetape4k-docs`가 이전 `exposed-r2dbc-workshop` lesson을 찾았다.
- `gh issue list --assignee debop`으로 live assigned open queue를 확인했다.
- `gh issue list --search "withTables runCatching CancellationException cleanup"`으로 duplicate가 없음을 확인했다.
- `./gradlew :exposed-r2dbc-shared:test --tests "exposed.r2dbc.shared.tests.WithTablesTest"`가 `BUILD SUCCESSFUL`과 `1 passing`으로 완료됐다.

## 향후 지침

Suspend test helper에서는 generic failure capture 전에 `CancellationException`을 다시 던진다. Cleanup failure는 suppressed 또는 logged 상태가 될 수 있지만 cancellation은 coroutine test scope에 계속 보여야 한다.
