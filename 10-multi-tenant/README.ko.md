# Chapter 10 Multi-Tenant WebFlux Examples

[English](./README.md)

Chapter 10은 Spring WebFlux + Exposed R2DBC tenant routing 전략을 비교합니다.
tenant 격리를 schema switch, tenant별 R2DBC pool, authorization gate, runtime
onboarding 중 어디에 둘지 선택할 때 이 문서에서 시작하세요.

## Strategy Map

| Module | 선택 기준 | 격리 모델 | 검증 |
|---|---|---|---|
| [`03-multitenant-spring-webflux`](./03-multitenant-spring-webflux/README.ko.md) | 하나의 database를 공유하고 요청마다 Exposed transaction에서 schema를 전환할 때 | 하나의 R2DBC database, tenant별 schema | H2 및 PostgreSQL-capable tests; CI와 Nightly 포함 |
| [`04-connection-factory-per-tenant-spring-webflux`](./04-connection-factory-per-tenant-spring-webflux/README.ko.md) | tenant set이 startup 시점에 정해져 있고 tenant마다 별도 pool이 필요할 때 | tenant별 R2DBC URL과 pool | H2-focused tests; `Examples.yml` 포함, project-wide CI assertion은 H2로 gate |
| [`05-spring-security-tenant-authorization-spring-webflux`](./05-spring-security-tenant-authorization-spring-webflux/README.ko.md) | routing 전에 authenticated tenant와 `X-TENANT-ID`가 일치해야 할 때 | tenant routing 전 authorization | H2-focused security/error tests; `Examples.yml` 포함, project-wide CI assertion은 H2로 gate |
| [`06-tenant-onboarding-spring-webflux`](./06-tenant-onboarding-spring-webflux/README.ko.md) | runtime에 tenant를 만들고 metadata 예약, pool provisioning, schema seed, cleanup이 필요할 때 | runtime tenant registry + tenant별 pool | H2-focused onboarding/failure tests; `Examples.yml` 포함, project-wide CI assertion은 H2로 gate |
| [`07-multitenant-ktor`](./07-multitenant-ktor/README.ko.md) | ReactorContext나 Spring filter 없이 Ktor에서 같은 schema-per-tenant 요청 흐름을 보고 싶을 때 | 하나의 R2DBC database, tenant별 schema, Ktor call attributes | H2-focused Ktor request tests; `Examples.yml` 포함 |

## Request Contracts

요청 라우팅 예제는 `X-TENANT-ID`가 누락, 공백, 잘못된 형식, 미등록 tenant이면
fail closed로 처리합니다. `05`는 tenant context를 쓰기 전에 authentication과
authorization을 추가합니다. `06`은 `X-ADMIN-TOKEN`으로 보호되는 admin
onboarding API를 추가합니다. `07`은 `X-TENANT-ID`를 인증되지 않은 workshop
routing signal로만 사용하며, 운영 환경에서는 tenant routing을 identity와
연결해야 한다고 문서화합니다.

## Verification

Chapter 10 예제는 다음 명령으로 로컬 검증합니다.

```bash
repo-test-summary -- ./gradlew \
  :03-multitenant-spring-webflux:test \
  :04-connection-factory-per-tenant-spring-webflux:test \
  :05-spring-security-tenant-authorization-spring-webflux:test \
  :06-tenant-onboarding-spring-webflux:test \
  :07-multitenant-ktor:test \
  -PuseDB=H2 \
  --continue \
  --console=plain
```

`Examples.yml`은 해당 모듈들이 바뀐 pull request와 push에서 같은 chapter 10
set을 실행합니다. Nightly의 H2 full shard도 모든 모듈을 실행합니다. non-H2
PostgreSQL/MySQL Nightly shard는 의도적으로 `03-multitenant-spring-webflux`만
유지하고, MariaDB smoke shard는 chapter 10을 실행하지 않습니다. `04`, `05`,
`06`, `07`은 아직 PostgreSQL/MySQL/MariaDB tenant database surface를 추가하지 않은
H2 workshop 전략이기 때문입니다.

repository root에서 실행할 때는 고유한 Gradle project name을 사용합니다.

```bash
./gradlew :03-multitenant-spring-webflux:test
./gradlew :04-connection-factory-per-tenant-spring-webflux:test
./gradlew :05-spring-security-tenant-authorization-spring-webflux:test
./gradlew :06-tenant-onboarding-spring-webflux:test
./gradlew :07-multitenant-ktor:test
```
