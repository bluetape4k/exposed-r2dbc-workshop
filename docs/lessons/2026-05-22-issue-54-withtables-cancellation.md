# Issue 54 WithTables Cancellation

## Context

`withTables()` is shared R2DBC test infrastructure. It previously used
`runCatching` and broad `Throwable` handling around suspend cleanup paths, which
made coroutine cancellation handling rely on incidental behavior.

## Decision

Rethrow `CancellationException` before generic cleanup handling in all
`withTables()` cleanup paths. Keep ordinary cleanup failures as suppressed
exceptions on ordinary statement failures, but do not attach cleanup failures to
statement cancellation. If cancellation is raised by cleanup or recovery after a
statement failure, keep cancellation as the thrown exception and attach the prior
failure as suppressed diagnostic context.

## Outcome

The helper now preserves structured cancellation while retaining cleanup
diagnostics for non-cancellation failures.

## Verification

- `./gradlew :exposed-r2dbc-shared:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :exposed-r2dbc-shared:test --tests "exposed.r2dbc.shared.tests.WithTablesTest" -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :exposed-r2dbc-shared:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check`

## Future Guidance

Do not wrap suspend cleanup in `runCatching` when cancellation must propagate.
For shared test helpers, handle `CancellationException` explicitly before any
generic `Throwable` recovery path.
