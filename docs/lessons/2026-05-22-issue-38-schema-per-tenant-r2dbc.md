# Issue 38 Schema-per-tenant R2DBC 교훈

## 맥락

Issue #38은 새 빈 module이 아니라 기존 `10-multi-tenant/03-multitenant-spring-webflux` 예제를 대상으로 했다. 이 module에는 이미 actor/movie WebFlux controller, schema initialization, tenant propagation, README 파일, focused test가 있었다.

## 결정

기존 actor/movie domain과 `X-TENANT-ID` header contract는 유지하되, HTTP tenant resolution은 fail closed로 만든다.

- Tenant header가 없거나 blank이면 `400 Bad Request`를 반환한다.
- 알 수 없는 tenant header도 `400 Bad Request`를 반환한다.
- Direct non-WebFlux helper fallback의 `Tenants.DEFAULT_TENANT` 사용은 example call에만 남기고, 사용될 때 warning을 log한다.
- `korean`과 `english` schema에서 같은 actor ID를 대상으로 positive fixture assertion을 두어 read isolation을 증명한다.

## 결과

예제는 이제 `README.md`와 `README.ko.md`에 request tenant contract를 문서화하고, `TenantFilter` behavior를 그 contract에 맞추며, root CI와 Nightly 양쪽의 coverage note를 명확히 유지한다.

## 검증

- `./gradlew :03-multitenant-spring-webflux:compileKotlin --warning-mode all --console=plain` 통과.
- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`은 advisor fix 이후 15 tests를 6.4s에 통과.
- Final commit verification 전에 `git diff --check` 통과.
- IntelliJ diagnostics는 이 worktree가 IDE project list에 열려 있지 않아 사용할 수 없었고, compile/test evidence를 fallback으로 사용했다.
- Claude advisor gate는 KDoc, fallback logging, positive fixture assertion fix 이후 `P0=0, P1=0`으로 통과.

## 향후 지침

- Issue description이나 오래된 plan을 신뢰하기 전에 현재 module contents를 조회한다. 이 module은 이미 구현돼 있었다.
- `WebTestClient`는 request가 app에 도달하기 전에 single-space header value를 거부한다. Application-side blank tenant branch를 검증하려면 empty string header를 사용한다.
- 나중에 helper fallback을 제거한다면 README snippet과 test를 같은 PR에서 갱신해 WebFlux path와 direct-coroutine path가 계속 구분되게 한다.
