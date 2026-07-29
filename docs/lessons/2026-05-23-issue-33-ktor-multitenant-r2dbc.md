# Issue 33 Ktor R2DBC Multi-tenant 교훈

## 맥락

Issue #33은 chapter 10 schema-per-tenant Spring WebFlux R2DBC 예제와 대응되는 Ktor module `10-multi-tenant/07-multitenant-ktor`를 추가했다.

## 결정

Ktor call attribute를 request tenant carrier로 사용하고, Ktor module 안에 local `suspendTransactionWithTenant` helper를 유지한다. WebFlux helper와의 중복은 의도적이다. WebFlux는 ReactorContext propagation을 가르치고, Ktor는 ThreadLocal이나 Spring filter 없이 call-scoped tenant resolution을 가르친다.

`X-TENANT-ID`는 workshop routing signal로만 남긴다. README는 production system에서는 tenant routing을 authenticated identity에 묶어야 한다고 경고한다.

## 결과

Module은 Ktor 위에서 `GET /actors`, `GET /actors/{id}`, `POST /actors`를 노출한다. Tests는 structured tenant error, duplicate header, write isolation, size `1` pool을 통한 빠른 tenant 교대, overlapping tenant request를 다룬다.

## 검증

- `./gradlew projects --console=plain`
- `./gradlew :07-multitenant-ktor:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks`: 9 tests passed.
- `repo-test-summary -- ./gradlew :03-multitenant-spring-webflux:test :04-connection-factory-per-tenant-spring-webflux:test :05-spring-security-tenant-authorization-spring-webflux:test :06-tenant-onboarding-spring-webflux:test :07-multitenant-ktor:test -PuseDB=H2 --continue --console=plain`: chapter 10 set passed.
- `./gradlew detekt --parallel --console=plain`
- `actionlint .github/workflows/Examples.yml`
- `git diff --check`
- PNG diagram: `docs/images/readme-diagrams/10-multi-tenant-07-multitenant-ktor-architecture-01.png`, 1400 x 760.
- IntelliJ diagnostics는 이 worktree에서 사용할 수 없었다(`project_not_found`). Compile/test/static checks를 fallback으로 사용했다.

## 향후 메모

- Raw tenant header value를 schema selection에 넘기지 않는다. 검증된 `Tenants.Tenant` enum instance만 Ktor plugin boundary를 넘어야 한다.
- Request route에는 bare `suspendTransaction`과 `runBlocking`을 두지 않는다.
- 이후 issue가 non-H2 Ktor tenant coverage를 추가하면 Examples/Nightly shard scope와 H2-only README wording을 다시 검토한다.
