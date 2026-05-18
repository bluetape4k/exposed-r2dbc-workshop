# CTE Query Builder Example

## Context

`bluetape4k-dependencies` published `1.0.1-SNAPSHOT`, which manages
`bluetape4k-exposed` `1.8.1-SNAPSHOT`. The R2DBC workshop can now consume the
new CTE Query Builder API through the managed dependency line.

## Lesson

For R2DBC workshop examples, update the dependencies BOM first and then verify
that version catalog aliases match the published artifact IDs. The current
Exposed artifacts use `bluetape4k-exposed-*` names.

## Evidence

- Added `Ex51_CteQueryBuilder` beside the existing raw SQL `Ex50_RecursiveCTE`.
- Verified `CteTable` and R2DBC `withCte` with H2 through the `:01-dml:test`
  fast path.

## Future Guard

When a BOM snapshot changes managed artifact names, check the resolved POM and
catalog aliases before adding example code.
