# Issue 35 Ktor R2DBC Routing Datasource 교훈

## 맥락

Issue #35는 Spring WebFlux `03-routing-datasource` 예제에 대응되는 Ktor chapter 11 module을 추가한다. 중요한 차이는 request context다. Spring은 Reactor Context와 transaction operator를 사용하지만, Ktor는 call-scoped attribute와 repository로 넘기는 explicit value를 사용한다.

## 결정

- Chapter 11에는 이미 `04`, `05` Ktor cache module이 있으므로 `11-high-performance/06-routing-datasource-ktor-r2dbc`를 추가한다.
- `RoutingRequestPlugin`으로 call마다 `X-Tenant-Id`와 `X-Read-Only`를 한 번 검증한다.
- Repository method에는 ThreadLocal, Reactor Context, global mutable state 대신 `RoutingRequest`를 명시적으로 전달한다.
- 네 개의 H2 R2DBC target(`default:rw`, `default:ro`, `acme:rw`, `acme:ro`)에 서로 다른 marker row를 seed해서, tests가 echoed route metadata가 아니라 선택된 database 자체를 증명하게 한다.
- `X-Read-Only: true`가 있을 때 `PATCH /routing/marker`는 caller input 충돌을 조용히 덮어쓰지 않고 reject한다.
- Ktor `application.conf`는 유지하지 않는다. Explicit test module과 `main()`이 plugin double-install 및 default resource leak을 피한다.

## 검증

- `./gradlew projects :06-routing-datasource-ktor-r2dbc:compileKotlin :06-routing-datasource-ktor-r2dbc:compileTestKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`
- `repo-test-summary -- ./gradlew :03-routing-datasource:test :04-cache-strategies-ktor-r2dbc:test :05-cache-strategies-ktor-r2dbc-coroutines:test :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain`
- `repo-test-summary -- ./gradlew :06-routing-datasource-ktor-r2dbc:test -PuseDB=H2 --continue --console=plain`
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

Codex 6-Tier review와 Claude Code CLI 6-Tier review는 blank-marker와 `application.conf` 지적을 고친 뒤 모두 `P0 = 0`, `P1 = 0`으로 통과했다.
