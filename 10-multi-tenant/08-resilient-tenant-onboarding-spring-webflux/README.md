# Resilient Tenant Onboarding with Spring WebFlux

[한국어](./README.ko.md)

This workshop extends the runtime-onboarding idea without changing the introductory `06-tenant-onboarding-spring-webflux` module. It shows how durable ownership, a finite lease, and startup reconciliation make an onboarding flow recoverable after an interrupted request or process restart.

## What the lifecycle record owns

Each tenant has one durable record containing its current state, attempt, reservation token, optimistic version, lease expiry, timestamps, and a stable failure category. The record intentionally excludes raw exception messages, R2DBC URLs, and credentials.

| State | Meaning |
| --- | --- |
| `PROVISIONING` | One reservation token owns a bounded preparation attempt. |
| `ACTIVE` | Durable resources are ready and can be published or republished after a restart. |
| `FAILED` | The metadata remains available for inspection and a later retry. |

Every update checks the tenant ID, reservation token, and version. A stale attempt therefore cannot mark a newer retry as failed or active.

## Operator API

Both endpoints use the workshop-only `X-ADMIN-TOKEN` header. Production services must replace it with authenticated authorization and secret handling.

| Request result | Meaning |
| --- | --- |
| `201 Created` | This request claimed, prepared, probed, and published the tenant. |
| `200 OK` | The same display name is already active; no preparation runs again. |
| `202 Accepted` | A non-expired reservation is still preparing; poll its lifecycle. |
| `409 Conflict` | The tenant ID belongs to a different display name. |
| `500 Internal Server Error` | Preparation failed; the response contains only the stable failure category. |

```bash
curl -i http://localhost:8080/api/admin/tenants \
  -H 'X-ADMIN-TOKEN: workshop-admin' \
  -H 'Content-Type: application/json' \
  --data '{"tenantId":"acme","displayName":"Acme"}'

curl -i http://localhost:8080/api/tenants/acme \
  -H 'X-ADMIN-TOKEN: workshop-admin'
```

## Database profiles

The default `h2` profile keeps the example fast and self-contained:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun
```

The `postgres` profile uses one shared PostgreSQL database. Durable lifecycle
records stay in `public.tenant_lifecycle`, while each tenant gets an isolated
`tenant_<tenant-id>` schema:

| Data | PostgreSQL location |
| --- | --- |
| Onboarding state, lease, attempt, failure category | `public.tenant_lifecycle` |
| Tenant readiness and future business tables | `tenant_<tenant-id>` |

Hyphens in a tenant ID become underscores, so `clinic-seoul` maps to
`tenant_clinic_seoul`.

```bash
POSTGRES_HOST=localhost \
POSTGRES_PORT=5432 \
POSTGRES_DATABASE=postgres \
POSTGRES_USERNAME=postgres \
POSTGRES_PASSWORD=postgres \
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun \
  --args='--spring.profiles.active=postgres'
```

Schema creation commits before table preparation. If a later preparation or
probe step fails, the lifecycle row becomes `FAILED` and the schema remains
available for diagnosis and an idempotent retry. Startup recovery does not run
provisioning DDL: it restores a reference to the expected schema, probes the
existing readiness marker, and publishes only a healthy tenant.

## Recovery boundary

At application startup, expired `PROVISIONING` rows become `FAILED(RECOVERY)` and remain visible. Each stored `ACTIVE` tenant is probed before its connection factory is placed in the process-local registry. A persisted `ACTIVE` row by itself is never enough to route a request.

The H2 file-database variant remains useful for a fast restart-recovery test,
while the PostgreSQL profile proves schema creation, isolation, failure
retention, and full application-context restart recovery against a real
container. This workshop still does not provide automatic schema deletion,
distributed locking, an external secret manager, production authorization,
a production-tuned connection pool, or a multi-node control plane.

## Verify

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
```
