# 03 Routing DataSource (Exposed R2DBC + Spring WebFlux)

`exposed-workshop`의 `03-routing-datasource` 설계를 바탕으로, Exposed R2DBC + Spring WebFlux 환경에서 테넌트 + 읽기/쓰기 분리 라우팅을 구현한 예제입니다.

## 핵심 구성

- `ConnectionFactoryRegistry`: 라우팅 키별 `ConnectionFactory` 등록/조회
- `ContextAwareRoutingKeyResolver`: `<tenant>:<rw|ro>` 키 계산
- `DynamicRoutingConnectionFactory`: Reactor Context 기반 동적 위임
- `TenantRoutingWebFilter`: `X-Tenant-Id`/경로 정보를 Reactor Context에 적재
- `RoutingTransactionalExecutor`: `TransactionalOperator`와 read-only 라우팅 힌트 브리지
- `RoutingMarkerRepository`: Exposed R2DBC로 라우팅 결과 검증

## 라우팅 규칙

- tenant 키: `X-Tenant-Id` 헤더 (미지정 시 `default`)
- read-only 키: `/routing/marker/readonly` 경로 또는 `X-Read-Only: true`
- 최종 라우팅 키: `<tenant>:<rw|ro>`

## Endpoint

- `GET /routing/marker`: read-write 마커 조회
- `GET /routing/marker/readonly`: read-only 마커 조회
- `PATCH /routing/marker`: 현재 tenant의 read-write 마커 갱신

## 테스트 실행

```bash
./gradlew :03-routing-datasource:test
```
