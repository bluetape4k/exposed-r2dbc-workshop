# Issue #42 Chapter 10 R2DBC Strategy Wiring 교훈

## 맥락

Issue #42는 chapter 10 Spring WebFlux R2DBC strategy example을 chapter-level documentation과 focused verification에 연결한다.

## 결정

Root README를 newest module에 직접 연결하지 않고 `10-multi-tenant/README.md`와 `README.ko.md`를 strategy entry point로 사용한다. Chapter README는 schema-per-tenant, connection-factory-per-tenant, tenant authorization, runtime onboarding을 selection criteria, isolation model, verification surface 기준으로 비교한다.

`Examples.yml`은 이제 `03-multitenant-spring-webflux`를 포함한 모든 chapter 10 Spring WebFlux R2DBC strategy module을 실행한다. Nightly의 H2 full shard는 이미 모든 module을 실행한다. Non-H2 PostgreSQL/MySQL Nightly shard는 의도적으로 `03`만 유지하고, MariaDB smoke shard는 chapter 10을 실행하지 않는다. `04`, `05`, `06`은 H2-focused workshop strategy이기 때문이다.

## 결과

Root README는 reader를 chapter-level strategy map으로 보낸다. Focused example CI는 이제 모든 chapter 10 Spring WebFlux strategy module을 다루고 네 module의 test report를 모두 upload한다.

## 검증

- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test -PuseDB=H2 --continue --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

## 향후 지침

Chapter-level strategy docs를 추가할 때 root README entry는 newest leaf module이 아니라 chapter-level README에 연결한다. Discoverability와 verification이 drift하지 않도록 `Examples.yml`을 chapter README의 strategy table과 맞춘다.
