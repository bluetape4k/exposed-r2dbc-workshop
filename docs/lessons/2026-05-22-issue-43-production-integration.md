# Issue #43 Production Integration 교훈

## 맥락

Chapter 12에는 production-grade Exposed R2DBC service pattern을 보여 주는 Spring Boot 4와 Ktor paired example이 필요했다.

## 결정

Stack별로 하나씩 두 Gradle module을 사용하고, `app`, `auth`, `realtime`, `outbound`, `diagnostics` package-level slice를 통해 child issue traceability를 유지한다. 이렇게 하면 Spring/Ktor parity를 보존하면서도 작은 module 10개로 쪼개지 않는다.

## 결과

Spring WebFlux/SSE와 Ktor WebSocket/MockEngine 예제가 있는 `12-production-integration`을 추가했다. 두 stack 모두 account, work item, realtime outbox event, outbound idempotency record, readiness state를 Exposed R2DBC로 저장한다.

## 검증

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test "-PuseDB=H2" --continue --console=plain`
- Spring module: 4 tests passing.
- Ktor module: 5 tests passing.

## 향후 지침

App-boundary example에서는 H2 R2DBC에 `DB_CLOSE_DELAY=-1`을 사용한다. 그렇지 않으면 connection마다 빈 in-memory database를 볼 수 있다. App bootstrap 자체가 lesson scope가 아니라면 dialect matrix coverage는 repository level에 둔다. 새 end-to-end example은 `.github/workflows/Examples.yml`에 등록해 focused workflow가 full CI matrix와 별도로 workshop adoption을 보호하게 한다.
