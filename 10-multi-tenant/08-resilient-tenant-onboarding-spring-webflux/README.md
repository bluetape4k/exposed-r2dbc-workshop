# Resilient Tenant Onboarding with Spring WebFlux

[한국어](./README.ko.md)

This workshop extends the runtime-onboarding idea without changing the introductory `06-tenant-onboarding-spring-webflux` module. It shows how durable ownership, a finite lease, and startup reconciliation make an onboarding flow recoverable after an interrupted request or process restart.

## What the lifecycle record owns

Each tenant has one durable record containing its current state, attempt, reservation token, optimistic version, lease expiry, timestamps, and a stable failure category. The record intentionally excludes raw exception messages, R2DBC URLs, and credentials.

| State | Meaning |
| --- | --- |
| `PROVISIONING` | One reservation token owns a bounded preparation attempt. |
| `ACTIVE` | The attempt completed and its runtime connection factory was published. |
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

## Recovery boundary

At application startup, expired `PROVISIONING` rows become `FAILED(RECOVERY)` and remain visible. Each stored `ACTIVE` tenant is probed before its connection factory is placed in the process-local registry. A persisted `ACTIVE` row by itself is never enough to route a request.

The H2 file-database variant is useful only for a restart-recovery test. It is not a distributed locking, PostgreSQL, authorization, or multi-node control plane example.

## Verify

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
```
