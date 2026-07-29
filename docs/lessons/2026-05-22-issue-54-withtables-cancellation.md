# Issue 54 WithTables Cancellation

## 맥락

`withTables()`는 shared R2DBC test infrastructure다. 이전 구현은 suspend cleanup path 주변에서 `runCatching`과 넓은 `Throwable` 처리를 사용해 coroutine cancellation handling을 우연한 동작에 의존하게 했다.

## 결정

모든 `withTables()` cleanup path에서 generic cleanup handling 전에 `CancellationException`을 다시 던진다. 일반 statement failure에서는 ordinary cleanup failure를 suppressed exception으로 유지하지만, statement cancellation에는 cleanup failure를 붙이지 않는다. Statement failure 이후 cleanup이나 recovery에서 cancellation이 발생하면 cancellation을 thrown exception으로 유지하고 prior failure를 suppressed diagnostic context로 붙인다.

## 결과

Helper는 이제 structured cancellation을 보존하면서 non-cancellation failure에 대한 cleanup diagnostic도 유지한다.

## 검증

- `./gradlew :exposed-r2dbc-shared:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :exposed-r2dbc-shared:test --tests "exposed.r2dbc.shared.tests.WithTablesTest" -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :exposed-r2dbc-shared:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check`

## 향후 지침

Cancellation propagation이 필요한 suspend cleanup은 `runCatching`으로 감싸지 않는다. Shared test helper에서는 generic `Throwable` recovery path보다 먼저 `CancellationException`을 명시적으로 처리한다.
