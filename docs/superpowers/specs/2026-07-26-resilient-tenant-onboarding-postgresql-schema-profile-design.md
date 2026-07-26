# 복구 가능한 테넌트 온보딩 PostgreSQL Schema 프로필 설계

## 배경

`10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux`는 H2를 사용해
lease와 token 기반 온보딩, 실패 이력 보존, 애플리케이션 재시작 뒤의 런타임 자원
복구를 설명한다. 현재 구현은 레지스트리와 테넌트 자원 모두 H2에 고정되어 있어,
실제 PostgreSQL에서 공유 데이터베이스와 테넌트별 schema를 조합하는 운영 형태를
실행해 볼 수 없다.

기존 설계는 #133의 최초 범위를 작게 유지하기 위해 PostgreSQL을 제외했다. 이번
후속 변경은 이미 완성된 생명주기 모델을 유지하면서, Spring profile로 저장소
구현을 선택할 수 있도록 범위를 확장한다.

## 목표

- 기본 `h2` profile과 기존 테스트의 동작을 보존한다.
- `postgres` profile에서 하나의 PostgreSQL 데이터베이스를 공유한다.
- `public` schema에는 테넌트 생명주기 레지스트리를 둔다.
- 테넌트 업무 데이터는 `tenant_<tenantId>` schema로 격리한다.
- 온보딩 성공, 실패, 재시도, 재시작 복구가 PostgreSQL에서도 같은 상태 계약을
  따른다는 것을 실제 컨테이너 통합 테스트로 증명한다.
- `10-multi-tenant/03-multitenant-spring-webflux`의 R2DBC schema 생성 및
  트랜잭션별 schema 전환 패턴을 재사용한다.

## 제외 범위

- 테넌트마다 별도의 PostgreSQL 데이터베이스 또는 DB 사용자를 만드는 방식
- schema 삭제를 포함한 tenant offboarding
- Flyway/Liquibase 기반 schema migration 자동화
- 다중 노드 분산 잠금과 외부 secret manager
- PostgreSQL 이외의 production database profile

## 검토한 선택지

### 선택지 A — URL과 dialect 속성만으로 하나의 설정에서 분기

설정 파일 수는 적지만 H2와 PostgreSQL의 자원 생성 방식이 한 factory에 섞인다.
잘못된 URL과 dialect 조합도 애플리케이션 시작보다 늦게 드러날 수 있다.

### 선택지 B — 공통 생명주기 설정과 profile별 인프라 설정 분리 (선택)

claim, provision, reconcile, publish 흐름은 공통 설정에 남기고, registry database와
runtime resource factory만 `h2`와 `postgres` profile 설정에서 제공한다. profile
경계가 bean 구성과 테스트에 그대로 드러나며, 데이터베이스별 DDL과 dialect가
섞이지 않는다.

### 선택지 C — PostgreSQL 전용 모듈 추가

격리는 가장 명확하지만 기존 08 모듈과 생명주기 코드 및 문서를 중복하게 된다.
이번 변경의 핵심은 같은 상태 모델 아래에서 profile별 저장소를 비교하는 것이므로
별도 모듈을 만들지 않는다.

## 전체 구조

```text
ResilientTenantOnboardingConfig
    ├── lifecycle repository
    ├── provisioner
    ├── reconciler
    └── runtime registry

H2 profile
    ├── H2 registry R2dbcDatabase
    └── H2TenantRuntimeResourceFactory

PostgreSQL profile
    ├── PostgreSQL registry R2dbcDatabase (public)
    └── PostgreSqlSchemaTenantRuntimeResourceFactory
            ├── CREATE SCHEMA IF NOT EXISTS tenant_<id>
            ├── tenant schema 업무 테이블 준비
            ├── schema와 테이블 probe
            └── schema 전환이 적용된 runtime resource 반환
```

기본 profile은 `h2`다. `postgres` profile을 활성화하면 H2 bean은 등록하지 않고
PostgreSQL bean만 등록한다. 공통 설정은 특정 dialect나 driver를 직접 알지 않는다.

## 설정 경계

`TenantLifecycleProperties`는 lease와 timeout 같은 공통 정책만 책임진다. 연결
정보는 profile별 속성으로 분리한다.

| profile | 속성 | 역할 |
| --- | --- | --- |
| `h2` | 기존 registry URL | 빠른 로컬 실행과 단위·재시작 테스트 |
| `postgres` | host, port, database, username, password | 공유 PostgreSQL 연결과 schema provisioning |

PostgreSQL 기본값은 로컬 개발에 유용한 비밀이 아닌 값만 제공한다. 자격 증명은 환경
변수로 덮어쓸 수 있어야 하며, 로그·API·생명주기 행에는 URL, 사용자 이름, 비밀번호를
기록하지 않는다. 연결 속성 객체의 문자열 표현도 비밀번호를 마스킹해 우발적인 설정
로그가 자격 증명을 노출하지 않게 한다.

registry `R2dbcDatabase`는 profile에 따라 `H2Dialect` 또는
`PostgreSQLDialect`를 명시한다. 생명주기 테이블은 PostgreSQL 기본 schema인
`public`에서 초기화하고 조회한다.

## Schema 이름 계약

외부 `tenantId`를 SQL 식별자로 직접 사용하지 않는다. 별도 `TenantSchemaName`
변환 경계에서 다음 규칙을 적용한다.

1. 입력 tenant ID는 기존 API 검증을 통과해야 한다.
2. 영문 소문자와 숫자는 유지한다.
3. 하이픈은 밑줄로 변환한다.
4. `tenant_` 접두사를 붙인다.
5. PostgreSQL 식별자 길이 제한을 넘는 값은 거부한다.
6. 생성된 이름은 SQL 문자열에 삽입하기 전 schema 식별자 규칙을 다시 검증한다.

예를 들어 `clinic-seoul`은 `tenant_clinic_seoul`로 변환된다. schema 이름은
생명주기 행에서 파생할 수 있으므로 별도 외부 입력이나 자격 증명으로 취급하지 않는다.

## 온보딩 절차

PostgreSQL factory는 claim을 소유한 요청에 대해 다음을 수행한다.

1. 공유 PostgreSQL connection factory로 연결한다.
2. 첫 번째 transaction에서 `CREATE SCHEMA IF NOT EXISTS tenant_<id>`를 실행하고
   커밋한다.
3. 두 번째 Exposed R2DBC transaction에서 해당 schema를 선택한다.
4. 업무 테이블과 필요한 초기 데이터를 멱등적으로 준비하고 커밋한다.
5. 별도 probe transaction에서 현재 schema와 필수 테이블을 확인한다.
6. probe를 통과한 runtime resource를 provisioner에 반환한다.
7. provisioner가 생명주기 행을 `ACTIVE`로 전이한 뒤 runtime registry에 공개한다.

Schema 선택은 `03-multitenant-spring-webflux`와 같이
`SchemaUtils.setSchema(Schema(name))`를 트랜잭션 시작 경계에서 실행한다. 연결 풀의
세션 상태가 다음 요청으로 새어 나갈 수 있다는 가정에 기대지 않는다. 모든 테넌트
업무 트랜잭션은 자신의 schema를 명시적으로 선택해야 한다.

runtime registry는 connection factory만 단독으로 노출하는 API 외에 schema 이름을
포함한 `TenantResources` 조회 API를 제공한다. PostgreSQL caller는 이 자원에서
schema를 읽고 schema-aware transaction helper를 사용해야 한다. connection
factory만 사용해 `public`에서 업무 쿼리를 실행하는 경로를 PostgreSQL 예제로
권장하지 않는다.

`TenantRuntimeResourceFactory`는 신규 온보딩의 `create`와 시작 재조정의
`restore`를 분리한다. `create`는 schema와 readiness table을 멱등적으로 준비하지만,
PostgreSQL `restore`는 파생된 schema 정보로 runtime resource만 재구성하고 DDL을
실행하지 않는다. reconciler는 `restore` 뒤 `probe`가 성공한 자원만 공개한다.

## 실패와 재시도

온보딩 실패 시 생성된 schema는 자동으로 삭제하지 않는다.

- 현재 token을 가진 시도만 생명주기 행을 `FAILED`로 전이한다.
- 실패한 시도가 만든 runtime resource는 닫되 schema와 이미 생성된 테이블은
  보존한다.
- schema 생성은 별도 transaction에서 먼저 커밋하므로 이후 테이블 준비나 probe가
  실패해도 생성된 schema는 롤백되지 않는다.
- 재시도는 같은 schema에 `CREATE SCHEMA IF NOT EXISTS`와 멱등적인 테이블 준비를
  다시 수행한다.
- schema 정리는 장애 조사와 보존 정책을 확인한 관리자가 별도의 offboarding
  절차에서 수행한다.

이 정책은 실패 증거를 보존하고, 늦게 도착한 이전 시도의 cleanup이 새 시도에서
준비한 데이터를 삭제하는 일을 방지한다.

## 재시작 재조정

애플리케이션 시작 시 기존 공통 reconciler 계약을 그대로 적용한다.

- 만료된 `PROVISIONING`은 `RECOVERY` 실패로 전이한다.
- `FAILED`는 공개하지 않는다.
- `ACTIVE`는 파생된 schema 이름으로 기존 runtime resource를 복원하고 probe한다.
- schema와 필수 테이블이 준비되어 있으면 runtime registry에 다시 공개한다.
- schema가 없거나 probe가 실패하면 해당 버전의 행만 `FAILED`로 전이한다.
- 시작 재조정 중 발생한 schema 또는 probe 실패는 일반 온보딩 실패와 구분해
  `RECOVERY` 코드로 기록한다.

PostgreSQL 재시작 통합 테스트에서는 첫 번째 애플리케이션 문맥을 닫은 뒤 같은
컨테이너와 데이터베이스를 사용하는 두 번째 문맥을 시작한다. 두 번째 문맥이
`ACTIVE` 행과 tenant schema를 확인하고 runtime registry를 복구해야 한다.

## 테스트 전략

### 기존 H2 회귀 테스트

- 기본 profile이 명시되지 않아도 H2 설정이 선택된다.
- 기존 repository, provisioner, HTTP, cancellation, restart 테스트가 모두
  변경 없이 통과한다.
- H2 재시작 테스트의 파일 데이터베이스 동작을 보존한다.

### PostgreSQL profile 테스트

`PostgreSQLServer.Launcher.postgres`를 사용하는 실제 컨테이너 통합 테스트를
추가한다.

1. `postgres` profile이 `PostgreSQLDialect`와 PostgreSQL factory를 선택한다.
2. registry table은 `public`에 생성된다.
3. 온보딩이 `tenant_<id>` schema와 업무 테이블을 생성한다.
4. 두 테넌트의 schema와 데이터가 서로 격리된다.
5. 준비 단계 실패는 행을 `FAILED`로 남기고 schema를 자동 삭제하지 않는다.
6. 실패한 테넌트 재시도가 같은 schema에서 성공한다.
7. 애플리케이션 문맥 재시작 뒤 `ACTIVE` 테넌트가 probe를 거쳐 다시 공개된다.
8. 존재하지 않거나 불완전한 schema를 가진 `ACTIVE` 행은 `RECOVERY` 실패로
   전이된다.

컨테이너가 필요한 테스트는 단위 테스트와 구분되는 이름과 태그를 사용하되, 해당
모듈의 일반 `test` 실행에서도 검증 가능해야 한다. 저장소의 기존 Testcontainers
launcher 수명 주기를 재사용하고 Docker가 없는 환경의 동작은 기존 프로젝트 정책과
맞춘다. singleton 컨테이너를 공유하는 테스트의 tenant ID에는 짧은 무작위 suffix를
붙여 이전 실행에서 보존된 schema와 lifecycle 행이 다음 실행을 오염시키지 않게 한다.

## 문서 변경

- 모듈 `README.md`와 `README.ko.md`에서 H2 전용이라는 기존 제한을 수정한다.
- `h2`와 `postgres` 실행 예시, 환경 변수, schema 배치를 설명한다.
- H2는 학습·빠른 테스트 profile이고 PostgreSQL은 schema 동작을 검증하는 통합
  profile임을 명시한다.
- 자동 schema 삭제, 분산 잠금, production 인증까지 보장한다고 표현하지 않는다.

## 수용 기준

- profile을 지정하지 않으면 기존 H2 애플리케이션과 테스트가 동작한다.
- `postgres` profile은 공유 PostgreSQL 데이터베이스와 `public` registry를
  사용한다.
- 각 테넌트는 검증된 `tenant_<id>` schema를 가진다.
- 업무 트랜잭션은 schema를 명시적으로 선택하며 다른 테넌트 데이터에 접근하지
  않는다.
- 실패한 schema는 보존되고 재시도는 멱등적으로 이어진다.
- PostgreSQL에서 온보딩, 격리, 실패, 재시도, 재시작 복구가 통합 테스트로
  검증된다.
- README의 설정과 실행 명령이 실제 profile 및 테스트 구성과 일치한다.

## 위험과 대응

| 위험 | 대응 |
| --- | --- |
| tenant ID를 통한 SQL 식별자 주입 | 별도 schema 이름 타입과 허용 문자·길이 재검증 |
| 연결에서 이전 tenant schema가 유지됨 | 모든 업무 트랜잭션 시작 시 schema 명시 |
| 실패 cleanup이 유효한 데이터를 삭제함 | 자동 `DROP SCHEMA` 금지, runtime resource만 닫음 |
| H2와 PostgreSQL 설정이 동시에 등록됨 | 상호 배타적인 profile 설정과 문맥 테스트 |
| registry query가 tenant schema로 전환됨 | registry 전용 database는 `public` 계약 유지 |
| 컨테이너 테스트가 기존 빠른 테스트를 불안정하게 만듦 | 기존 launcher와 수명 주기를 재사용하고 profile 테스트를 분리 |

## 설계 검토

| Priority | Lens | Finding | 반영 |
| --- | --- | --- | --- |
| P1 | Stability | PostgreSQL DDL은 transaction rollback 대상이므로 schema와 테이블 준비를 한 transaction에 두면 실패 schema 보존 계약이 깨진다. | schema 생성 transaction을 먼저 커밋하고 준비·probe transaction을 분리했다. |
| P1 | Security / User | shared connection factory만 조회하면 caller가 tenant schema 선택을 누락할 수 있다. | schema를 포함한 `TenantResources` 조회와 schema-aware transaction 사용 계약을 추가했다. |
| P1 | Stability / Ops | 시작 probe 실패의 코드가 기존 구현의 `PROBE`와 설계의 `RECOVERY` 사이에서 불일치했다. | startup reconciliation 실패는 `RECOVERY`로 통일했다. |
| P1 | Security | Kotlin data class 문자열 표현이 PostgreSQL password를 노출할 수 있다. | 연결 속성 문자열 표현에서 password를 마스킹하도록 고정했다. |
| P1 | Stability | singleton PostgreSQL 컨테이너에 보존된 schema가 고정 ID 테스트를 오염시킬 수 있다. | 컨테이너 테스트 tenant ID에 무작위 suffix를 사용한다. |
| P1 | Stability / Ops | reconciler가 온보딩용 `create`를 재사용하면 누락된 schema를 새로 만들어 손상을 숨긴다. | factory에 DDL 없는 `restore`를 추가하고 startup은 restore 후 probe만 수행한다. |
| P2 | Performance | raw driver connection factory는 pool보다 연결 비용이 크다. | 이번 workshop 범위에서는 정합성 학습을 우선하고 pool 도입은 보류한다. README에 production pool을 보장하지 않음을 명시한다. |

최신 통합 검토 결과는 Performance, Stability, Security, Operator/Ops,
Developer/API, User/caller 모든 관점에서 P0=0, P1=0이다.
