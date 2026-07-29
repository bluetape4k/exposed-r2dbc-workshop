# Issue #36 Ktor R2DBC 문서와 검증 교훈

## 맥락

Issue #36은 Ktor multi-tenant, cache, coroutine cache, routing datasource module이 들어온 뒤 Ktor R2DBC chapter 10과 11 discoverability pass를 닫는다.

## 배운 점

- `./gradlew projects`가 runnable task prefix의 source of truth다. Ktor examples는 `:07-multitenant-ktor`, `:06-routing-datasource-ktor-r2dbc`처럼 unique leaf project name을 사용한다. `:exposed-r2dbc-11-high-performance-06-routing-datasource-ktor-r2dbc` 같은 prefixed name은 이 repository에서 유효하지 않다.
- `Examples.yml`은 touched PR과 push에서 새 Ktor module을 명시적으로 gate한다. Nightly는 H2 full shard를 통해 이들을 다루며, non-H2 Nightly shard는 실제 multi-database surface가 있는 module로 의도적으로 제한한다.
- Root README discoverability에는 chapter index뿐 아니라 direct Ktor link도 포함해야 한다. 그래야 독자가 chapter layout을 추측하지 않고 Ktor equivalent를 찾을 수 있다.

## 향후 방어선

Example module을 추가하거나 이름을 바꾼 뒤에는 README update를 게시하기 전에 `./gradlew <task> --dry-run` 또는 `./gradlew <module>:tasks`로 문서화된 모든 Gradle task를 검증한다.
