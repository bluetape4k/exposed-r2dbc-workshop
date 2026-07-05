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

## Verification

```bash
./gradlew :04-starrocks-olap-local:test -PuseDB=H2 --console=plain
```
