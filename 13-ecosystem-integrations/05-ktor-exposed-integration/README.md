# Explicit Ktor Exposed R2DBC Integration

English | [Korean](README.ko.md)

This example shows a Ktor application whose Exposed integration is explicitly
R2DBC-first. Routes call a repository backed by `suspendTransaction`, and the
application owns the R2DBC pool lifecycle.

## Boundary

| Layer | Responsibility |
| --- | --- |
| Ktor routes | JSON request/response, status mapping, health and readiness endpoints |
| Repository | validation and Exposed R2DBC `suspendTransaction` calls |
| Exposed | `WorkshopNotes` schema and `Flow` result collection |
| Resources | caller-owned R2DBC pool and `ApplicationStopped` cleanup |

The source `exposed-workshop` example is JDBC-first and uses Hikari,
`Database`, and `exposedJdbcTransaction`. Those APIs are intentionally not
used here. The module does not add `exposed-jdbc`, Hikari, or a blocking
transaction bridge.

## Routes

- `POST /api/notes` validates and inserts a note.
- `GET /api/notes` collects the Exposed result `Flow` in ascending id order.
- `GET /healthz/exposed` returns static application liveness.
- `GET /readyz/exposed` executes a small R2DBC query.
- `GET /api/failures/sql` demonstrates sanitized database error mapping.

The pool is configured with `maxSize = 2`, `initialSize = 1`, and `minIdle = 0`.
`KtorExposedIntegrationResources` owns that pool and disposes it when Ktor emits
`ApplicationStopped`; calling `close()` more than once is safe.

## Run

```bash
./gradlew :05-ktor-exposed-integration:test -PuseDB=H2
```

The tests use Ktor `testApplication` and an in-memory H2 R2DBC pool. No Docker
container or external service is required.

## Tested behavior

- note creation and listing through R2DBC transactions;
- health/readiness responses and closed-resource failure mapping;
- pool bounds, caller ownership, and idempotent cleanup;
- structured validation and sanitized error responses.

## Relationship to chapter 12

Chapter 12 remains the production-service reference for authentication,
sessions, outbox delivery, and observability. This smaller chapter 13 example
isolates the Ktor/Exposed R2DBC ownership boundary so it can be compared with
the Spring Modulith custom publication log in the next stack.
