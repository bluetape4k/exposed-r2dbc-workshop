# Tenant Onboarding WebFlux 예제

[English](./README.md)

이 chapter 10 모듈은 Spring WebFlux 서비스가 tenant metadata를 만들고,
격리된 R2DBC 리소스를 provision한 뒤, Exposed 테이블을 생성/seed하고,
애플리케이션 재시작 없이 이후 요청을 새 tenant로 라우팅하는 흐름을 보여줍니다.

런타임에 tenant를 만들고 tenant마다 별도 `ConnectionFactory`와 Exposed
`R2dbcDatabase`가 필요할 때 이 전략을 선택하세요. Shared-table tenancy는
`03-multitenant-spring-webflux`, 시작 시점에 tenant pool이 이미 정해진 경우는
`04-connection-factory-per-tenant-spring-webflux`, 이미 존재하는 tenant route를
인증하는 것이 핵심이면 `05-spring-security-tenant-authorization-spring-webflux`를
사용합니다.

![Tenant onboarding R2DBC flow](../../docs/assets/readme-diagrams/issue-41-tenant-onboarding-r2dbc-01.png)

## Onboarding Flow

```http
POST /api/tenants
X-ADMIN-TOKEN: workshop-admin
Content-Type: application/json

{
  "tenantId": "alpha",
  "displayName": "Alpha Tenant"
}
```

1. `TenantRegistryRepository`가 tenant row를 `PROVISIONING`으로 예약합니다.
2. `TenantProvisioner`가 tenant H2 R2DBC pool과 Exposed database를 만듭니다.
3. Exposed `SchemaUtils.create(...)`가 actor/movie schema를 생성합니다.
4. 라우팅 격리를 검증할 수 있도록 seed data를 넣습니다.
5. `TenantConnectionFactoryRegistry`에 runtime resource를 등록합니다.
6. Registry row를 `ACTIVE`로 바꿉니다.

Onboarding 후 요청은 앞 chapter 10 connection-factory 예제와 같은 routing
header를 사용합니다.

```http
GET /actors
X-TENANT-ID: alpha
```

## 실패와 중복 처리

| 상황 | 결과 |
|---|---|
| `X-ADMIN-TOKEN` 누락 또는 오류 | `401 Unauthorized` |
| tenant id 형식 오류 또는 빈 display name | `400 Bad Request` |
| active/provisioning tenant 중복 | `409 Conflict` |
| tenant 상한 초과 | `429 Too Many Requests` |
| provisioning 실패 | `500 Internal Server Error`; runtime pool과 registry row 제거 |

Reservation 경로는 global onboarding mutex와 per-tenant mutex를 함께 사용하므로
동일 tenant 요청이 두 개의 pool로 경합하지 않습니다. 실패 row는 cleanup 후 또는
startup recovery가 stale row를 `FAILED`로 표시한 뒤에만 재사용할 수 있습니다.

## Registry and Resource Model

- Registry database는 tenant id, display name, database name, R2DBC URL,
  status, creation time을 저장합니다.
- Runtime resource는 `TenantConnectionFactoryRegistry`에 있고, 실패 cleanup과
  애플리케이션 종료 시 독립적으로 dispose됩니다.
- 이 워크숍은 H2 in-memory tenant database를 사용합니다. 재시작하면 이전
  `ACTIVE` row는 실제 pool이 사라졌으므로 `FAILED`로 표시되고 tenant를 다시
  onboard해야 합니다.
- H2가 아닌 운영 URL에는 credential이 들어갈 수 있습니다. Metadata table에
  plaintext credential을 저장하지 말고 secret manager, encryption, rotation,
  audit log를 사용해야 합니다.
- Offboarding, quota billing, cross-region provisioning은 이 예제의 범위가
  아닙니다.

## 실행

```bash
./gradlew :06-tenant-onboarding-spring-webflux:test -PuseDB=H2
```

이 모듈은 H2 전용입니다. `Examples.yml`의 chapter 10 example workflow에서
검증합니다. 이 이슈는 H2 워크숍 provisioning flow를 추가하는 것이므로, Nightly
matrix 확장은 새 multi-database compatibility surface를 다루는 #42로 미룹니다.
