# Dependencies 1.1.1 Sync

## Context

`bluetape4k-dependencies` 1.1.0 was superseded by 1.1.1 after the artifact
availability audit found generated aliases for non-published mock web
application modules. This workshop consumes the shared catalog and also runs a
database matrix that can expose repository-specific R2DBC behavior after
artifact availability is fixed.

## Decision

Consume `bluetape4k-dependencies = "1.1.1"` through the standard shared-version
sync path. Keep catalog availability fixes separate from R2DBC compatibility
fixes.

## Outcome

PR #85 aligned this repository to the 1.1.1 catalog and merged after CI passed.
The PostgreSQL matrix then exposed an Exposed R2DBC batch insert generated-key
issue, recorded separately in
`docs/lessons/2026-05-23-r2dbc-batch-insert-generated-values.md`.

## Verification

- `./gradlew :exposed-r2dbc-shared:test -PuseDB=POSTGRESQL --max-workers=1 --continue`
- GitHub PR #85 status checks passed before merge.
- Workspace-level `scripts/sync-shared-versions.py --workspace .. --check --summary`
  passed after the downstream PRs were merged.

## Future Guidance

When the shared catalog patch fixes publication availability, wait until Maven
Central `repo1` resolves the new version before rerunning downstream CI. If a
database matrix then fails, preserve the dependency sync and fix the
repository-specific database behavior in the same PR or a follow-up patch.
