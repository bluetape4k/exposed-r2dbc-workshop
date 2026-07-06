# Trino Session Options Boundary

[English](README.md) | [한국어](README.ko.md)

This module keeps Trino session options visible without starting a Trino
server. It renders local Exposed R2DBC SQL and builds the Trino EXPLAIN request
shape plus headers.

## Scenario

- Render SQL from `TrinoLineItems` inside an R2DBC transaction.
- Build `TrinoQuerySession` headers for catalog, schema, source, client tags,
  and session properties.
- Wrap the local SQL with `EXPLAIN` to document the handoff boundary.

## Diagrams

The ERD shows the local line-item table, session option model, and EXPLAIN
handoff shape. The sequence diagram shows how SQL rendering and Trino headers
stay visible without starting a Trino coordinator.

![Trino session options ERD](../../docs/assets/readme-diagrams/issue-115-chapter13-02-trino-session-options-erd-01.png)

![Trino session options sequence](../../docs/assets/readme-diagrams/issue-115-chapter13-02-trino-session-options-sequence-01.png)

## Verification

```bash
./gradlew :02-trino-session-options:test -PuseDB=H2 --console=plain
```
