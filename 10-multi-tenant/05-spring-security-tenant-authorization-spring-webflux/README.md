# Spring Security Tenant Authorization WebFlux Example

[한국어](./README.ko.md)

This chapter 10 module shows tenant authorization before Exposed R2DBC routes a
request to a tenant-specific `ConnectionFactory`.

Use this strategy when an authenticated request must prove that its tenant
identity matches the tenant selected by `X-TENANT-ID`. Use
`04-connection-factory-per-tenant-spring-webflux` when you only want to study
connection-factory routing without an authentication boundary.

![Spring Security Tenant Authorization architecture](../../docs/images/readme-diagrams/10-multi-tenant-05-spring-security-tenant-authorization-architecture-01.png)

## Authentication Sources

| Source | Example | Tenant source |
|---|---|---|
| JWT bearer token | `Authorization: Bearer korean-token` | JWT `tenant_id` claim |
| API key | `X-API-KEY: demo-korean-key` | fixed demo key map |
| Demo session | `X-DEMO-SESSION: korean-session` | fixed demo header map |

`X-DEMO-SESSION` is a workshop-only header. It is not a production session
cookie or login flow. CSRF is disabled because this example is a stateless JSON
API; cookie-backed session flows should keep CSRF protection.

## Request Contract

```http
GET /actors
X-TENANT-ID: korean
X-API-KEY: demo-korean-key
```

| Case | Result |
|---|---|
| authenticated tenant equals `X-TENANT-ID` | `200 OK` |
| missing authentication | `401 Unauthorized` |
| invalid API key or demo session | `401 Unauthorized` |
| missing, blank, malformed, or unknown `X-TENANT-ID` | `400 Bad Request` |
| authenticated tenant differs from `X-TENANT-ID` | `403 Forbidden` |
| JWT lacks a usable `tenant_id` claim | `403 Forbidden` |

Only `AuthorizedTenantContextWebFilter` writes `TenantContextKeys.TENANT_ID` to
Reactor context. The request header is never trusted by itself, so Exposed R2DBC
cannot silently fall back to the default tenant on HTTP request paths.

## Run

```bash
./gradlew :05-spring-security-tenant-authorization-spring-webflux:test -PuseDB=H2
```

This module is H2-only. Root CI covers it through the H2 test job, and
`Examples.yml` also runs it in the chapter 10 example workflow. Nightly DB matrix
coverage is unchanged because this example does not introduce PostgreSQL,
MySQL, or MariaDB tenant databases.
