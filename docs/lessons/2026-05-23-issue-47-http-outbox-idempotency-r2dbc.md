# Issue #47 HTTP Outbox Idempotency R2DBC 교훈

## 맥락

Issue #47은 Spring Boot 4 WebFlux와 Ktor 양쪽에 outbound HTTP client example을 추가해 chapter 12를 확장한다. Exposed workshop analog는 이미 topic shape를 증명했지만, 이 repository에서는 R2DBC/coroutine boundary를 명시적으로 유지해야 하며 기존 두 chapter 12 module 밖에 topic-specific module을 만들면 안 된다.

## 결정

- 작업은 `01-spring-production-integration`, `02-ktor-production-integration` 안의 paired `outbound` slice로 유지한다.
- Outbound HTTP row는 dispatch 전에 저장하고, database-unique idempotency key를 duplicate boundary로 삼는다.
- Outbound idempotency key는 persistence 전에 검증한다. 이 값이 dispatch 중 `Idempotency-Key` HTTP header로 replay되기 때문이다.
- `PENDING` 및 retry 가능한 row를 짧은 `suspendTransaction` 안에서 `IN_FLIGHT`로 claim하고, Spring WebClient 또는 Ktor HTTP client call은 transaction 밖에서 수행한 뒤, terminal/retryable state를 새 transaction에 기록한다.
- Retryable failure는 세 번으로 제한하고 sanitized error text를 저장해 credential-like value가 durable row data가 되지 않게 한다.
- 각 outbound dispatch attempt는 repository default timeout으로 제한하고, timeout은 retry 가능한 transport status `599`로 기록한다. Remapped transport exception은 sanitized row state를 저장하기 전에 WARN으로 log한다.
- Replaceable delivery interface와 Ktor `MockEngine`을 사용해 실제 external HTTP service 없이 outbound behavior를 test한다.

## 결과

Spring/Ktor examples는 success, retry-to-success, retry exhaustion, permanent client failure, duplicate idempotency key, permission denial, invalid target validation, timeout mapping, cross-repository duplicate enqueue protection, concurrent single-send dispatch를 다룬다. README diagram은 `docs/images/readme-diagrams/` 아래 SVG source에서 생성한 committed PNG를 사용한다.

## 검증

Chapter 12 module에는 단일 Gradle invocation을 사용한다.

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:cleanTest :02-ktor-production-integration:cleanTest :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain
```

## 향후 방어선

이 repository에서 outbound HTTP example을 별도 topic module로 옮기지 않는다. Chapter design을 명시적으로 다시 정하지 않는 한, 향후 chapter 12 production slice도 기존 Spring/Ktor module 내부 package slice로 유지한다.
