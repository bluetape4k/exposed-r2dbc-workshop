# 명시적 Ktor Exposed R2DBC 통합

[English](README.md) | 한국어

이 예제는 Exposed 통합을 R2DBC-first로 구성한 Ktor 애플리케이션을 보여준다.
route는 `suspendTransaction` 기반 repository를 호출하고 애플리케이션이
R2DBC pool의 수명을 직접 소유한다.

## 경계

| 계층 | 책임 |
| --- | --- |
| Ktor route | JSON 요청/응답, 상태 매핑, health/readiness endpoint |
| Repository | 검증과 Exposed R2DBC `suspendTransaction` 호출 |
| Exposed | `WorkshopNotes` schema와 Flow 결과 수집 |
| Resources | 호출자가 소유하는 R2DBC pool과 `ApplicationStopped` 정리 |

원본 `exposed-workshop` 예제는 JDBC-first이며 Hikari, `Database`,
`exposedJdbcTransaction`을 사용한다. 이 모듈은 해당 API를 의도적으로 사용하지
않는다. `exposed-jdbc`, Hikari, blocking transaction bridge도 추가하지 않는다.

## Route

- `POST /api/notes`: 노트를 검증하고 저장한다.
- `GET /api/notes`: Exposed 결과 `Flow`를 id 오름차순으로 수집한다.
- `GET /healthz/exposed`: 정적 애플리케이션 liveness를 반환한다.
- `GET /readyz/exposed`: 간단한 R2DBC query를 실행한다.
- `GET /api/failures/sql`: 데이터베이스 오류를 민감정보 없이 매핑하는 예제다.

pool은 `maxSize = 2`, `initialSize = 1`, `minIdle = 0`으로 설정한다.
`KtorExposedIntegrationResources`가 pool을 소유하고 Ktor의
`ApplicationStopped` 이벤트에서 dispose한다. `close()`를 여러 번 호출해도
안전하다.

## 실행

```bash
./gradlew :05-ktor-exposed-integration:test -PuseDB=H2
```

테스트는 Ktor `testApplication`과 H2 인메모리 R2DBC pool을 사용한다. Docker
container나 외부 서비스가 필요하지 않다.

## 검증하는 동작

- R2DBC transaction을 통한 노트 생성·조회;
- health/readiness 응답과 종료된 resource 오류 매핑;
- pool 크기, 호출자 소유권, 멱등적인 cleanup;
- 구조화된 validation 및 민감정보가 제거된 오류 응답.

## 12장과의 관계

12장은 인증, session, outbox delivery, observability를 포함한 production
service 기준 예제다. 이 13장 예제는 Ktor/Exposed R2DBC 소유권 경계만 작게
분리해 다음 스택의 Spring Modulith custom publication log와 비교할 수 있게
한다.
