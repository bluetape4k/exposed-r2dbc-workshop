# WIP Audit WithTables Cancellation

## Context

The 2026-05-18 qmd-backed exposed-r2dbc-workshop audit checked prior WIP refresh
and CTE Query Builder lessons, live GitHub issue state, and shared helper code.

## Decision or Finding

`withTables()` is shared R2DBC test infrastructure and currently wraps suspend
cleanup in `runCatching` plus broad `Throwable` handling. That preserves ordinary
cleanup diagnostics, but it does not explicitly protect coroutine
`CancellationException` propagation.

## Outcome

Registered GitHub issue #54 and refreshed `WIP.md` from 0 assigned issues to 19
live assigned issues.

## Verification

- `qmd query ... --no-rerank -c bluetape4k-docs` surfaced prior
  exposed-r2dbc-workshop lessons.
- `gh issue list --assignee debop` confirmed the live assigned open queue.
- `gh issue list --search "withTables runCatching CancellationException cleanup"`
  found no duplicate.
- `./gradlew :exposed-r2dbc-shared:test --tests "exposed.r2dbc.shared.tests.WithTablesTest"`
  completed with `BUILD SUCCESSFUL` and `1 passing`.

## Future Guidance

For suspend test helpers, rethrow `CancellationException` before generic failure
capture. Cleanup failures may be suppressed or logged, but cancellation must stay
visible to the coroutine test scope.
