# 07-multitenant-ktor

[English](./README.md)

Chapter 10의 Ktor + Exposed R2DBC schema-per-tenant 예제입니다.
`03-multitenant-spring-webflux`의 actor/movie 흐름을 유지하되, 요청 tenant는
Reactor context가 아니라 Ktor call attributes로 전달합니다.

## Architecture

![Ktor R2DBC multi-tenant request flow](../../docs/images/readme-diagrams/10-multi-tenant-07-multitenant-ktor-architecture-01.png)

## 이 모듈의 핵심

| 영역 | 결정 |
|---|---|
| Tenant header | `X-TENANT-ID`, 값은 `korean` 또는 `english` |
| Tenant validation | 누락, 공백, 충돌하는 중복 값, 미등록 값은 `INVALID_TENANT` JSON과 함께 `400` |
| Request context | Ktor `ApplicationCall.attributes`; ThreadLocal/ReactorContext 미사용 |
| DB isolation | 하나의 H2 R2DBC pool, tenant별 schema |
| Transaction boundary | `suspendTransactionWithTenant(tenant, db)`가 transaction 시작 시 schema 전환 |

`X-TENANT-ID`는 workshop용 routing signal일 뿐 authentication/authorization이
아닙니다. 운영 환경에서는 tenant routing을 인증된 principal 또는 서명된 tenant
claim과 먼저 연결해야 합니다.

## Endpoints

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/actors` | 현재 tenant schema의 actor 목록 조회 |
| `GET` | `/actors/{id}` | 현재 tenant schema에서 actor 단건 조회 |
| `POST` | `/actors` | 현재 tenant schema에 actor 추가 |

## 테스트 실행

```bash
repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain
```

테스트는 tenant별 성공 경로, 구조화된 `400` 오류, 중복 header 처리, write
isolation, pool size `1`에서 빠른 tenant 교차 호출, 겹치는 Ktor 요청을 검증합니다.

## Notes

이 모듈은 WebFlux helper를 공유하지 않고 local tenant transaction helper를
둡니다. 두 모듈은 병렬 workshop 예제입니다. WebFlux는 ReactorContext를,
Ktor는 call-scoped attributes를 보여줍니다.

Schema-per-tenant routing은 각 transaction 안에서 `SET SCHEMA`를 실행합니다.
학습에는 단순하고 명확하지만 transaction마다 비용이 있고 connection-state reset이
정확해야 합니다. pool-level routing은 #35의 chapter 11 routing datasource
예제로 이어집니다.
