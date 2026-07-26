# R2DBC WebFlux 테넌트 온보딩 복구성 설계

## 문제와 목표

`10-multi-tenant/06-tenant-onboarding-spring-webflux`는 런타임에 테넌트를 만들고
R2DBC pool, Exposed `R2dbcDatabase`, 스키마를 준비하는 기본 흐름을 설명한다. 다만
실패하면 레지스트리 행을 삭제하고, 시작 시에는 `PROVISIONING`과 `ACTIVE` 행을 모두
`FAILED`로 바꾼다. 이 방식은 H2 메모리 예제의 단순한 정리 규칙으로는 적합하지만,
중단된 작업의 원인·시도 횟수·소유권을 설명하거나 재시작 뒤 실제 자원을 재조정하는
운영 흐름을 보여 주지 못한다.

이 변경은 새 `08-resilient-tenant-onboarding-spring-webflux` 모듈에서 다음을
실행 가능한 예제로 제공한다.

- 테넌트 온보딩 시도의 내구성 있는 상태와 실패 이력
- 같은 테넌트의 재시도와 동시 요청을 하나의 소유권 전이로 수렴시키는 방식
- 시작 시 레지스트리 상태가 아니라 실제 R2DBC 자원 준비 여부로 판단하는 재조정
- 영속 상태가 준비 완료라고 확인된 뒤에만 런타임 라우터에 공개하는 순서

기존 06 모듈은 변경하지 않는다. 이 모듈은 기초적인 예약·생성·정리 흐름을,
08 모듈은 복구성과 운영 경계를 가르친다.

## 현재 근거

- 06의 `TenantRegistryRepository.reserve`는 `FAILED` 행을 바로
  `PROVISIONING`으로 바꾸거나 새 행을 삽입한다. 시도 횟수, 실패 원인, 갱신 시각,
  소유권 버전은 저장하지 않는다.
- 06의 `TenantProvisioner.cleanupAfterFailure`는 runtime registry와 pool을
  정리한 다음 메타데이터 행을 삭제한다. 따라서 실패한 작업을 진단하거나 안전하게
  재시도할 영속 근거가 남지 않는다.
- 06의 `recoverStaleRows`는 H2 메모리 pool이 사라졌다는 전제 때문에 모든
  `PROVISIONING` 및 `ACTIVE` 행을 `FAILED`로 전이한다.
- 06의 runtime registry는 프로세스 메모리에만 존재하고, `register` 뒤에
  `markActive`를 호출한다. 즉 영속 준비 상태와 라우팅 공개 순서가 분리되어 있지
  않다.
- 기존 06 테스트는 성공, 중복, 용량, 실패 정리, 미등록 테넌트 거부를 검증한다.
  중단 작업의 이력, 토큰 불일치, 재시작 재조정, 동시 소유권은 검증하지 않는다.

## 선택지와 결정

### 선택지 A — 06 모듈을 직접 확장

가장 적은 파일을 바꾸지만, 처음 읽는 독자가 기본 온보딩과 운영 복구를 한 흐름으로
읽게 된다. 06의 목적과 실패 정리 예제가 흐려진다.

### 선택지 B — 새 08 고급 모듈을 추가 (선택)

06의 기초 예제를 보존하고, 08에서 상태 전이·토큰 소유권·재조정만을 별도 실습으로
설명한다. 일부 코드가 06과 닮지만, 독자가 어느 경계가 추가되었는지 비교할 수 있고
각 README가 책임을 명확히 설명할 수 있다.

### 선택지 C — PostgreSQL/Testcontainers 기반 제어 평면까지 함께 도입

가장 실제 환경에 가깝지만 새 driver, 컨테이너, CI/Nightly 전략과 데이터베이스별
운영 요구사항을 한 이슈에 묶는다. #133의 범위를 넘어가므로 채택하지 않는다.

## 설계

### 모듈과 저장소

새 모듈 경로는
`10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux`이다. 독립적인
Spring WebFlux 애플리케이션과 테스트를 갖고, 06의 actor/movie 데이터 모델은
라우팅 격리를 확인하는 최소 수단으로만 사용한다.

레지스트리 행은 다음 정보를 가진다.

| 필드 | 용도 |
| --- | --- |
| `tenantId`, `displayName`, `databaseName`, `r2dbcUrl` | 테넌트와 자원 위치 |
| `status` | `PROVISIONING`, `ACTIVE`, `FAILED` |
| `attempt` | 첫 생성부터 증가하는 시도 횟수 |
| `reservationToken` | 현재 작업 소유자를 식별하는 UUID |
| `version` | 조건부 갱신에 쓰는 낙관적 버전 |
| `lastFailureCode` | `RESERVE`, `POOL`, `SCHEMA`, `PROBE`, `PUBLISH`, `RECOVERY` 중 마지막 실패 단계. 원본 예외 메시지, URL, 토큰, 자격 증명은 저장하지 않음 |
| `createdAt`, `updatedAt`, `leaseExpiresAt` | 진단과 만료된 작업 판단 |

`TenantMetadata`와 공개 응답에는 상태, 시도 횟수, 갱신 시각,
`lastFailureCode`를 포함한다. 내부 `reservationToken`과 원본 예외는 외부 API나
로그의 일반 정보 수준에 보내지 않는다.

### 소유권과 멱등성

`TenantLifecycleRepository.claim`은 레지스트리의 유일한 쓰기 진입점이다.

1. 새 테넌트는 `PROVISIONING`, `attempt=1`, 새 토큰, `version=1`로 삽입한다.
2. `FAILED` 행은 읽은 `version`과 일치할 때만 새 토큰과 다음 `attempt`로 갱신한다.
3. 같은 표시 이름의 `ACTIVE` 행은 성공한 기존 결과로 반환한다. 이 경우 pool을 다시
   만들지 않는다.
4. 유효한 lease를 가진 `PROVISIONING` 행은 진행 중 결과로 반환한다. HTTP 응답은
   `202 Accepted`이며 다른 작업자가 자원을 정리하거나 활성화할 수 없다.
5. 표시 이름이 다른 동일 테넌트 요청은 `409 Conflict`로 거부한다.

조건부 update가 0행을 갱신하면 저장소는 행을 다시 읽어 위 다섯 결과 중 하나로
재분류한다. 프로세스 내부 mutex는 최적화일 뿐 정합성의 근거가 아니다.
`leaseDuration`은 `overallTimeout`보다 길게 설정하며, provisioner는 각 외부 단계
앞에서 같은 토큰으로 lease를 연장한다. 타임아웃이나 취소가 발생하면 현재 토큰만
실패 전이를 수행한다.

### 준비, 활성화, 공개

새 `TenantProvisioner`는 claim을 소유한 요청만 다음 단계를 실행한다.

1. pool을 만들고 warm-up한다.
2. Exposed 스키마와 seed 데이터를 멱등적으로 준비한다.
3. 임시 자원 probe로 연결과 필요한 테이블을 확인한다.
4. 같은 `reservationToken`과 `version`을 조건으로 레지스트리를 `ACTIVE`로
   전이한다.
5. 그 뒤 runtime registry에 자원을 publish한다.

4와 5 사이에 프로세스가 종료되면 다음 시작의 재조정이 `ACTIVE` 행을 다시 probe하여
pool을 만들어 publish한다. 따라서 `ACTIVE`는 라우팅이 이미 공개되었다는 뜻이 아니라
자원이 준비되어 재공개할 수 있다는 영속 계약이다. 라우터는 metadata가 `ACTIVE`여도
runtime registry에 공개되지 않은 테넌트를 거부한다.

실패 또는 취소 시 현재 토큰을 가진 작업만 `FAILED`로 바꾸고 `lastFailureCode`와
`updatedAt`을 남긴다. cleanup은 `NonCancellable` 구역에서 현재 작업이 만든 pool만
닫는다. 이전 작업의 늦은 실패가 새 토큰의 `ACTIVE` 자원이나 행을 삭제할 수 없다.

### 시작 재조정

`TenantLifecycleReconciler`는 애플리케이션 시작 뒤 다음을 순서대로 처리한다.

- 만료된 `PROVISIONING` lease: 소유자를 알 수 없는 중단 작업으로 `FAILED` 전이
- 유효 lease의 `PROVISIONING`: 아직 다른 인스턴스가 소유한 작업으로 보존
- `ACTIVE`: URL을 사용해 짧은 수명의 probe pool을 만들고 스키마 준비 여부를 확인
  - 성공: 새 runtime pool과 database를 만들고 registry에 publish
  - 실패: 해당 버전의 행만 `FAILED`로 전이하고 원인을 기록
- `FAILED`: runtime registry에 공개하지 않음

기본 실행과 테스트는 H2를 사용한다. 재시작 재조정 테스트는 임시 디렉터리의 H2 file
URL로 registry와 tenant DB를 유지한다. 이는 H2 기반의 재현 가능한 학습 경계이며,
분산 제어 평면이나 실제 운영 데이터베이스의 증명은 아니다.

### API와 오류 계약

`POST /api/tenants`의 관리자 인증 방식은 06과 같은 workshop token을 유지한다.
`GET /api/tenants/{tenantId}`는 token을 요구하며, 호출자가 `202 Accepted` 뒤의
상태와 안전한 실패 코드를 polling할 수 있게 한다. 권한 주체·멤버십·역할 모델은
05의 별도 예제와 다음 이슈의 범위다.

| 결과 | HTTP | 의미 |
| --- | --- | --- |
| 새 claim이 완료됨 | `201 Created` | 새 테넌트가 `ACTIVE`가 되어 공개됨 |
| 이미 `ACTIVE` | `200 OK` | 같은 요청의 멱등 결과 |
| 다른 소유자가 준비 중 | `202 Accepted` | 유효 lease의 `PROVISIONING` 상태 |
| 다른 표시 이름 | `409 Conflict` | 같은 tenant id의 상충 요청 |
| 준비 또는 재조정 실패 | `500 Internal Server Error` | 행은 `FAILED`와 안전한 실패 요약을 보존 |
| 준비되지 않은 테넌트 라우팅 | `404 Not Found` | `ACTIVE`가 아니거나 runtime registry에 없음 |
| 상태 조회 | `200 OK` 또는 `404 Not Found` | 상태, 시도 횟수, 갱신 시각, 안전한 실패 코드만 반환 |

## 실패 모드와 대응

| 실패 모드 | 대응 | 검증 |
| --- | --- | --- |
| pool 또는 스키마 준비 중 예외 | 현재 토큰으로만 `FAILED`, pool 정리, 실패 이력 보존 | 각 failure point 뒤의 행·registry·attempt 검사 |
| 활성화 뒤 publish 전 중단 | `ACTIVE` 행을 남기고 다음 시작에서 probe 후 publish | runtime registry를 비운 뒤 reconciler 실행 |
| 오래된 작업의 늦은 cleanup | token/version 조건 실패로 무시 | 새 claim 뒤 이전 토큰의 fail 시도 |
| 동시 POST | DB 조건부 claim이 한 소유자만 선택 | 병렬 요청이 하나의 attempt와 동일 최종 상태로 수렴 |
| resource URL은 있지만 DB가 없음 | probe 실패 후 `FAILED`, 라우팅 비공개 | file DB를 제거하거나 probe simulator 실패 |

## 검증과 문서화

테스트는 JUnit 5, `runSuspendIO`, bluetape4k assertions를 사용한다. 실제 IO가 필요한
WebFlux/H2 테스트에는 `runSuspendIO`를 쓰고, 동시성 테스트는 기존
`MultithreadingTester`를 우선 검토한다. 맞는 helper가 없으면 coroutine barrier를
사용한 이유와 실제 동시 실행 증거를 테스트에 남긴다.

다음 문서를 함께 갱신한다.

- 새 모듈의 `README.md`, `README.ko.md`
- `10-multi-tenant/README.md`, `README.ko.md`
- 루트 `README.md`, `README.ko.md`
- `.github/workflows/Examples.yml`의 경로, Gradle task, 테스트 결과 artifact

## 수용 기준과 완료 정의

- 상태 행은 시도 횟수, 토큰, 버전, 실패 요약, lease 시각을 유지한다.
- 새·실패 재시도·활성·진행 중·충돌 요청이 명확한 HTTP 계약으로 구분된다.
- 이전 소유자는 새 소유자의 상태나 runtime 자원을 변경할 수 없다.
- 시작 재조정은 `ACTIVE`를 일괄 실패 처리하지 않고 resource probe 결과로 publish 또는
  실패 전이를 결정한다.
- `ACTIVE`가 아닌 테넌트와 아직 runtime registry에 없는 테넌트는 라우팅되지 않는다.
- 성공, 실패 재시도, 멱등, 동시성, 토큰 불일치, 재조정, 라우팅 거부 테스트가 존재한다.
- 모든 README 로케일과 Examples workflow 등록이 새 모듈의 실제 이름 및 검증 명령과
  일치한다.

## 제외 범위

- JWT/OIDC, 테넌트 멤버십, 역할 기반 권한 모델
- PostgreSQL 또는 다른 production driver와 Testcontainers/Nightly matrix 추가
- offboarding, 과금, quota, cross-region provisioning

## 설계 검토

| Lens | 결과 | 반영 |
| --- | --- | --- |
| Performance | P0/P1 없음 | DB 조건부 claim을 정합성 근거로 두고 프로세스 mutex는 필수 경로에서 제외한다. |
| Stability | P0/P1 없음 | lease를 전체 timeout보다 길게 두고 외부 단계마다 연장하며, token 조건 cleanup과 재조정을 명시했다. |
| Security | P0/P1 없음 | 실패 이력은 고정된 실패 코드만 저장하고 URL, 토큰, 자격 증명, 원본 예외를 저장하거나 노출하지 않는다. |
| Operator/Ops | P0/P1 없음 | 시작 재조정 결과, failure code, 상태 조회 API, H2 file 기반 재시작 실습 경계를 명시했다. |
| Developer/API | P0/P1 없음 | 06과 08을 분리하고 상태·소유권·라우팅 공개 순서와 검증 항목을 고정했다. |
| User/caller | P0/P1 없음 | `202 Accepted`를 받은 호출자가 `GET /api/tenants/{tenantId}`로 상태를 확인할 수 있게 했다. |
