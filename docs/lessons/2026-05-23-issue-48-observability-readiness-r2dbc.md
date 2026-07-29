# Issue 48 Observability Readiness R2DBC 교훈

## 맥락

Chapter 12에는 기존 production-integration slice와 가까운 Spring WebFlux/Ktor observability/readiness example이 필요했다. 이 feature는 request correlation, structured error request id, diagnostic operation persistence, live database readiness check를 두 stack에 모두 추가한다.

## 교훈

- `TimeoutCancellationException`은 ordinary coroutine cancellation이 아니라 readiness timeout으로 다룬다. Bounded health ping이 bubbling 대신 degrade해야 할 때는 `CancellationException`보다 먼저 catch한다.
- Synthetic diagnostic delay는 `suspendTransaction` 밖에 둔다. Duration을 측정한 뒤 완료된 operation row만 저장한다.
- Spring/Ktor 예제에서 같은 durable table shape를 사용한다. 그래야 workshop이 domain model drift가 아니라 framework boundary를 비교한다.
- 이 repository의 README diagram은 `docs/images/readme-diagrams/` 아래 committed PNG asset으로 둔다. 유용할 때만 SVG source를 곁에 보관한다.

## 검증

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks
```

Spring/Ktor diagnostic endpoint와 request-correlation test를 추가한 뒤 targeted H2 run에서 31 tests가 성공했다.
