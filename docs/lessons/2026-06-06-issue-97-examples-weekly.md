# Issue 97 Examples Weekly Gate

## Context

`exposed-r2dbc-workshop` already had an `Examples` workflow for selected
multi-tenant, high-performance, and production-integration examples. Issue #97
required that workflow to become a weekly downstream R2DBC scenario gate while
retaining manual dispatch and path-filtered PR coverage.

## Decision

- Add one weekly schedule to the existing `Examples` workflow.
- Keep the selected examples on the H2/default smoke path for PR and scheduled
  runs.
- Leave heavier external database validation to CI/Nightly, not this
  consumer-facing Examples gate.

## Outcome

The workflow remains separate from CI and Nightly, keeps `workflow_dispatch`,
keeps push/PR path filters, and already uploads test result artifacts for each
chapter job.

## Verification

- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

## Future Guidance

When adding R2DBC downstream examples, prefer the weekly Examples workflow for
representative consumer smoke coverage and avoid adding external credentials to
this path.
