# Issue #40 Spring Security Tenant Authorization R2DBC 교훈

## 맥락

Issue #40은 기존 Exposed R2DBC multi-tenant routing 예제 위에 tenant-aware Spring Security를 보여 주는 chapter 10 workshop module을 추가했다.

## 결정

Request tenant routing과 authentication을 분리한다. Demo API key, demo session, JWT는 Spring Security 안에서 authenticated tenant를 확정한다. `AuthorizedTenantContextWebFilter`만 `TenantContextKeys.TENANT_ID`를 쓰며, authenticated tenant가 `X-TENANT-ID`와 일치한 뒤에만 쓴다.

JWT tenant claim 문제는 authentication failure가 아니라 authorization failure다. 따라서 JWT converter는 token을 받아들이고, missing 또는 unknown tenant claim은 tenant authorization filter에 남겨 403으로 처리한다.

## 결과

새 `05-spring-security-tenant-authorization-spring-webflux` module은 API-key, JWT, demo-session tenant authorization path를 다룬다. `Examples.yml`은 이제 기존 chapter 10 routing 예제와 함께 새 module을 실행한다.

## 검증

- Claude spec/plan re-gate: `P0=0`, `P1=0`, PASS.
- Claude code-review retry with `claude -p --model opus --verbose --output-format stream-json`: `P0 = 0`, `P1 = 0`, PASS.
- `./gradlew :05-spring-security-tenant-authorization-spring-webflux:compileKotlin --warning-mode all --console=plain`
- `./gradlew :05-spring-security-tenant-authorization-spring-webflux:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :05-spring-security-tenant-authorization-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- `./gradlew projects --console=plain`
- `./gradlew detekt --parallel --console=plain`

## 향후 지침

Tenant authorization 예제에서는 request header가 R2DBC routing context를 직접 쓰게 두지 않는다. Single writer를 고정하는 architecture test를 추가하고, authenticated bad-header 400과 unauthenticated bad-header 401 ordering을 모두 다뤄 filter-order regression이 보이게 한다.
