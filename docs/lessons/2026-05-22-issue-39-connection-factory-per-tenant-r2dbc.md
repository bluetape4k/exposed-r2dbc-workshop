# Issue 39 Tenant별 Connection Factory R2DBC 교훈

## 맥락

Issue #39는 Spring WebFlux + Exposed R2DBC에서 tenant별 R2DBC connection factory와 pool을 보여 주기 위해 chapter 10 module `04-connection-factory-per-tenant-spring-webflux`를 추가했다.

## 결정

`korean`, `english`로 고정된 tenant registry와 함께 Spring `AbstractRoutingConnectionFactory`를 사용한다. HTTP request는 필수 `X-TENANT-ID`를 통해 fail closed로 처리한다. HTTP를 거치지 않는 직접 routing은 lookup key가 없을 때 Spring default target을 계속 사용할 수 있다. Exposed request work는 suspend transaction boundary를 지나도 Reactor context를 보존하는 `TenantTransactionExecutor`를 통한다.

## 결과

Module은 schema-per-tenant 예제의 actor API shape를 유지하면서 isolation 방식을 schema state에서 tenant 소유 R2DBC pool로 바꾼다. README 파일은 pool-count tradeoff와 tenant authorization이 issue #40 scope임을 문서화한다.

## 검증

- `./gradlew projects --console=plain`
- `./gradlew :04-connection-factory-per-tenant-spring-webflux:compileKotlin --warning-mode all --console=plain`
- `repo-test-summary -- ./gradlew :04-connection-factory-per-tenant-spring-webflux:test "-PuseDB=H2" --continue --console=plain`: 33 tests passed.
- `git diff --check`
- Claude gate: Step 2-R, Step 3-R, Step 6-R 모두 `P0=0`, `P1=0` 도달.
- IDE diagnostics는 이 worktree가 IntelliJ에 열려 있지 않아 사용할 수 없었고, compile/test evidence를 fallback으로 사용했다.

## 향후 메모

- Architecture test allowlist를 의도적으로 갱신하지 않는 한 `TenantTransactionExecutor`를 유일한 request-path Exposed transaction boundary로 유지한다.
- `R2dbcDatabase` instance는 registry-owned pool을 재사용한다. Ownership과 shutdown을 재설계하지 않는 한 independent initializer pool을 추가하지 않는다.
- 나중에 non-H2 profile을 추가한다면 Nightly shard coverage를 다시 검토한다.
