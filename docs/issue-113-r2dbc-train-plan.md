# Epic #113 R2DBC 통합 예제 stacked PR train 계획

## 목표

Epic #113은 `exposed-workshop` 12/13장의 통합 예제를 현재 저장소의
R2DBC-first 학습 계약으로 이식한다. 소스의 JDBC 구현을 그대로 복사하지 않고,
각 예제의 경계를 `suspendTransaction`, `Flow`, 공유 테스트 인프라로 다시
설계한다.

현재 `develop`은 `origin/develop`과 동일하고 작업 트리는 clean이다. #114와
#115는 완료되었으며, 이 문서는 남은 #116/#117 구현과 최종 통합의 결정 원장이다.

## stacked PR 순서

| 순서 | 브랜치 | PR base | 책임 범위 | 완료 기준 |
| --- | --- | --- | --- | --- |
| 1 | `docs/issue-113-r2dbc-train-plan` | `develop` | 설계·결정·검증 계획 | 이 문서와 이슈 결정 기록이 live read-back 됨 |
| 2 | `feat/issue-116-ktor-r2dbc` | 1번 브랜치 | `05-ktor-exposed-integration` | Ktor 요청·저장소·자원 수명이 R2DBC만 사용하고 H2 테스트가 통과 |
| 3 | `feat/issue-116-spring-modulith-r2dbc` | 2번 브랜치 | `06-spring-modulith-publications` | custom R2DBC publication log와 Modulith 경계 테스트가 통과 |
| 4 | `feat/issue-117-ddd-aggregate-r2dbc` | 3번 브랜치 | `07-ddd-aggregate-repository` | aggregate/line/event 원자성·rollback 테스트가 통과 |
| 5 | `feat/issue-117-boundaries-r2dbc` | 4번 브랜치 | `08-ddd-modulith-boundaries` | `ApplicationModules.verify()`와 음성 fixture 테스트가 통과 |
| 6 | `integration/issue-113-r2dbc-ecosystem-train` | `develop` | README·CI·최종 DoD 통합 | 전체 H2 검증과 live PR/issue read-back 완료 |

개별 PR은 `Refs #113`, `Refs #116`, `Refs #117`을 사용하고 자동 close
토큰은 사용하지 않는다. 최종 integration PR에서만 완료된 native child와
Epic close 가능 여부를 확인한다. 현재 #113은 본문 체크박스 참조는 있지만
GitHub `sub_issues` 응답은 비어 있으므로, close 전에 native 관계를 다시 읽고
필요한 경우 정확히 보정한다.

## 구현 결정

### `05-ktor-exposed-integration`

- Ktor route와 repository는 Exposed R2DBC `suspendTransaction`을 사용한다.
- JDBC `Database`, Hikari, `exposedJdbcTransaction`, blocking `.block()`은
  사용하지 않는다.
- connection/session 종료와 오류 전파는 coroutine cancellation을 보존한다.
- health/readiness, CRUD, pool lifecycle, SQL 민감정보 비노출을 deterministic
  H2 테스트로 검증한다.

### `06-spring-modulith-publications`

Spring Modulith의 공식 `EventPublicationRepository` SPI는 현재 동기
`List`/`Optional`/`void` 계약이며 공식 persistence 구현도 JDBC/JPA/
MongoDB/Neo4j 중심이다. 공식 R2DBC publication repository/starter로
표현하거나 동기 bridge를 event loop에 넣지 않는다.

- Spring Modulith core의 module metadata/listener semantics만 사용한다.
- durable publication 상태는 workshop-local Exposed R2DBC publication log와
  명시적인 dispatcher로 구현한다.
- README에는 이것이 native Modulith event registry가 아니라 custom R2DBC
  integration임을 적고, native registry는 blocking 비교 대상으로 분리한다.
- 참고: [Spring Modulith Events](https://docs.spring.io/spring-modulith/reference/events.html),
  [EventPublicationRepository API](https://docs.spring.io/spring-modulith/docs/2.1.1/api/org/springframework/modulith/events/core/EventPublicationRepository.html)

### `07-ddd-aggregate-repository`

- value object와 aggregate는 불변 상태를 기본으로 한다.
- order, line, domain event 기록은 하나의 `suspendTransaction` 안에서
  원자적으로 저장한다.
- `selectAll()` 등 Flow API는 경계에서 의도적으로 `toList()` 또는 필요한
  collector로 수집한다.
- 실패 hook을 이용해 line/event 일부만 남지 않는 rollback을 검증하고,
  production 코드에는 `!!`와 suspend `runCatching`을 사용하지 않는다.

### `08-ddd-modulith-boundaries`

- `@PackageInfo`, `@NamedInterface("events")`, `@ApplicationModule` 메타데이터와
  `ApplicationModules.verify()`를 유지한다.
- repository 구현은 R2DBC이며 boundary 검증은 외부 서비스 없이 deterministic
  local test로 수행한다.
- internal package를 참조하는 음성 fixture가 실제로 실패하는지 확인한다.

### DuckDB 범위

소스의 `09-duckdb-embedded-analytics`는 파일 기반 JDBC/embedded 예제이고
안정적으로 지원되는 R2DBC 경로가 확인되지 않았다. Epic #115에서 제외한
범위를 유지하며 #117 README에 evidence-backed N/A/opt-in 결정으로 기록한다.

## 공통 검증

각 단계에서 먼저 영향 모듈의 H2 테스트를 실행한다.

```bash
./gradlew :<module>:test -PuseDB=H2 --rerun-tasks --console=plain
```

최종 단계에서는 다음을 수행한다.

```bash
./gradlew projects --console=plain
./gradlew test -PuseDB=H2 --continue --console=plain
python3 .github/scripts/changed-r2dbc-test-tasks.py --help
actionlint .github/workflows/Examples.yml
git diff --check
```

변경 경로 selector가 새 13장 모듈을 모두 선택하는지 별도 JSON 입력으로
검증한다. 모듈 추가 시 `settings.gradle.kts`의 동적 include 규칙, `buildSrc`
catalog alias, README locale pair, test resources, Examples workflow를 함께
확인한다.

## PR·closeout 규칙

- 각 stacked PR은 이전 stack head를 base로 삼고, 현재 head와 body를 `gh`로
  live read-back한다.
- PR body는 한국어이며 마지막에 `## DoD Status`를 둔다.
- CI가 통과하고 review thread가 0개인지 확인한 뒤에도 merge하지 않는다.
- 최종 integration PR의 정확한 head에 대해 사용자의 fresh `승인`을 받은
  경우에만 merge한다.
- merge 후 local `develop`, `origin/develop`, final PR head의 SHA parity와
  소유 브랜치/워크트리 정리를 검증한다.

## 알려진 위험

1. 저장소 실제 version catalog가 repo overlay에 적힌 버전보다 최신이다. 현재
   catalog와 기존 모듈 패턴을 기준으로 작업하고 버전을 임의로 낮추지 않는다.
2. Spring Modulith native publication registry를 R2DBC라고 포장하면 blocking
   경계가 숨겨진다. custom log와 native registry를 문서·패키지 수준에서
   분리한다.
3. #113의 본문 checklist와 native `sub_issues` 상태가 다르다. 최종 close 전에
   두 표현을 모두 live read-back한다.
