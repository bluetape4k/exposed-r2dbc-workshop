# Issue #41 Tenant Onboarding R2DBC 교훈

## 맥락

Issue #41은 application restart 없이 runtime tenant metadata reservation, R2DBC pool provisioning, Exposed schema creation, seed data, request routing을 보여 주기 위해 chapter 10 module `06-tenant-onboarding-spring-webflux`를 추가했다.

## 결정

Tenant onboarding은 명시적인 admin operation으로 유지한다. `TenantProvisioner`는 cap-check, reservation, pool creation, schema creation, runtime registration, activation을 하나의 onboarding mutex 아래 직렬화해 tenant limit이 concurrent request와 race하지 않게 한다. Tenant별 mutex는 direct registry reuse를 계속 보호한다.

Tenant layer에서는 domain exception을 사용하고 `TenantApiExceptionHandler`에서 map한다. Controller만 admin-token boundary를 위해 HTTP-specific `ResponseStatusException`을 유지한다. Cleanup은 `NonCancellable`에서 실행하고 schema drop, pool close, metadata delete failure를 분리해 original provisioning failure를 보존한다.

## 결과

Module은 onboarding이 shared-table tenancy, fixed tenant pool, tenant authorization보다 나은 선택이 되는 시점을 문서화한다. README diagram은 `docs/images/readme-diagrams/` 아래 committed PNG를 사용하고 SVG source를 옆에 둔다. `Examples.yml`은 이제 다른 chapter 10 예제와 함께 onboarding module을 실행한다.

## 검증

- Claude Step 6-R full/slim/delta review artifact가 `P0=0`, `P1=0`에 도달했다.
- Codex 6-tier review는 cleanup, ordering, failure-path fix 이후 남은 P0/P1이 없다고 확인했다.
- `repo-test-summary -- ./gradlew :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`: 14 tests passed.
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `./gradlew detekt --parallel --console=plain`
- `git diff --check`

## 향후 지침

- Tenant pool lifecycle은 request path에서 nonblocking으로 유지한다. Suspend code에서 R2DBC pool warmup과 disposal을 호출할 때 `awaitSingle()`/`awaitSingleOrNull()`을 사용한다.
- Failure injection test는 post-registration cleanup뿐 아니라 모든 provisioning checkpoint를 다뤄야 한다.
- `bufferUntilChanged` grouping은 row를 flow로 변환하기 전에 group key에 대한 명시적인 SQL `orderBy`가 필요하다.
- 이 작업 중 local `bluetape4k-github` qmd sync에는 `exposed-r2dbc-workshop`이 포함되지 않았다. 이 repository의 issue snapshot을 qmd에 의존하기 전에 해당 collection을 refresh하거나 sync script를 확장한다.
