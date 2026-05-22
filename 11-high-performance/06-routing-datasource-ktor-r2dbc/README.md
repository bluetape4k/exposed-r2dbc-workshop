> Korean version: [README.ko.md](README.ko.md)

# 06 Routing Datasource Ktor R2DBC

This module is the Ktor counterpart to
[`03-routing-datasource`](../03-routing-datasource/README.md). It demonstrates
tenant plus read/write R2DBC routing without Spring WebFlux, Reactor Context, or
Spring transaction infrastructure.

## Architecture

![Ktor R2DBC routing datasource flow](../../docs/images/readme-diagrams/11-high-performance-06-routing-datasource-ktor-r2dbc-architecture-01.png)

## What It Shows

| Topic | Ktor implementation |
|---|---|
| Tenant routing | `X-Tenant-Id` resolves to `default` or `acme` |
| Read/write routing | `X-Read-Only: true` or `/readonly` selects the read-only target |
| Coroutine safety | Validated `RoutingRequest` is stored in Ktor call attributes and passed explicitly |
| DB target proof | Four H2 R2DBC pools store distinct marker rows: `default-rw`, `default-ro`, `acme-rw`, `acme-ro` |

`X-Tenant-Id` is optional. Missing or blank values fall back to `default`, which
matches the Spring WebFlux example. `X-Read-Only`, when present, must be
`true` or `false`; invalid values return `400 INVALID_ROUTING_REQUEST`.

## Routes

| Method | Path | Routing behavior |
|---|---|---|
| `GET` | `/routing/marker` | Reads from the selected tenant's `rw` or `ro` target |
| `GET` | `/routing/marker/readonly` | Forces `ro` target |
| `PATCH` | `/routing/marker` | Updates the selected tenant's `rw` target |

`PATCH /routing/marker` rejects `X-Read-Only: true` instead of silently
overriding the caller's routing hint.

## Limitations

This is a workshop example. Header-based routing is not authentication or
authorization. Production systems must bind tenant and read/write policy to an
authenticated principal, signed claim, or trusted gateway context. Graceful
request draining before pool disposal is also outside this module's scope. The
demo creates four small pools with `maxPoolSize=4`; production sizing should be
based on real target counts and traffic.

## Run Tests

```bash
repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain
```
