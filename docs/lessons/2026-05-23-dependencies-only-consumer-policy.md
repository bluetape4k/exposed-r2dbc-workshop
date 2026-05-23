# Dependencies-Only Consumer Policy

## Context

The R2DBC workshop already imported `bluetape4k-dependencies`, but the catalog
also pinned some bluetape4k artifacts directly. That duplicated version
ownership in a consumer repository.

## Decision

Keep `bluetape4k-dependencies` as the only bluetape4k version source and make
all bluetape4k artifact aliases versionless.

## Outcome

The catalog no longer carries direct bluetape4k artifact versions or separate
assertions aliases. Artifact resolution now follows the BOM.

## Verification

Ran forbidden-reference grep, `git diff --check`, and
`./gradlew compileKotlin --no-daemon --no-configuration-cache`.

## Future Guidance

When the ecosystem BOM changes, upgrade the BOM alias first and let the
catalog's bluetape4k artifacts remain versionless.
