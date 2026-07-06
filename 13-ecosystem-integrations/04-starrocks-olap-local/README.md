# StarRocks OLAP Local Boundary

[English](README.md) | [한국어](README.ko.md)

This module prepares StarRocks-style OLAP rollup rows through a local H2 R2DBC
projection. It exposes a JDBC URL preview for a real validation path but does
not open a StarRocks connection by default.

## Scenario

- Insert order events into `StarRocksOrderEvents`.
- Project daily regional revenue rollups through Exposed R2DBC.
- Render a grouped rollup SQL statement.
- Keep the StarRocks R2DBC applicability boundary explicit.

## Diagrams

The ERD shows local order events, DTO rows, regional rollups, and the documented
adapter profile. The sequence diagram shows the local projection plus the
documentation-only StarRocks handoff.

![StarRocks OLAP local ERD](../../docs/assets/readme-diagrams/issue-115-chapter13-04-starrocks-olap-local-erd-01.png)

![StarRocks OLAP local sequence](../../docs/assets/readme-diagrams/issue-115-chapter13-04-starrocks-olap-local-sequence-01.png)

## Verification

```bash
./gradlew :04-starrocks-olap-local:test -PuseDB=H2 --console=plain
```
