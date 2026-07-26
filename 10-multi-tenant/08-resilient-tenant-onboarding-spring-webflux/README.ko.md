# Spring WebFlux 장애 복구형 테넌트 온보딩

[English](./README.md)

이 워크숍은 입문용 `06-tenant-onboarding-spring-webflux` 모듈을 바꾸지 않고, 실행 중 테넌트 생성 흐름을 확장합니다. 영속 소유권, 유효 시간, 시작 시 재조정을 사용해 요청 중단이나 프로세스 재시작 뒤에도 온보딩을 복구하는 방식을 다룹니다.

## 수명주기 레코드가 관리하는 정보

테넌트마다 상태, 시도 횟수, 예약 토큰, 낙관적 버전, 유효 시간, 시각, 안정적인 실패 범주를 가진 영속 레코드가 하나 존재합니다. 원시 예외 메시지, R2DBC URL, 자격 증명은 의도적으로 저장하지 않습니다.

| 상태 | 의미 |
| --- | --- |
| `PROVISIONING` | 하나의 예약 토큰이 제한된 준비 시도를 소유합니다. |
| `ACTIVE` | 영속 자원 준비가 끝났으며, 현재 프로세스나 재시작한 프로세스에 게시할 수 있습니다. |
| `FAILED` | 메타데이터를 조회할 수 있으며 나중에 다시 시도할 수 있습니다. |

모든 갱신은 테넌트 ID, 예약 토큰, 버전을 함께 검사합니다. 따라서 오래된 시도가 새 재시도를 실패나 활성 상태로 바꿀 수 없습니다.

## 운영자 API

두 API는 워크숍 전용 `X-ADMIN-TOKEN` 헤더를 사용합니다. 운영 서비스에서는 이를 인증된 권한 검사와 비밀 관리로 교체해야 합니다.

| 요청 결과 | 의미 |
| --- | --- |
| `201 Created` | 이 요청이 테넌트를 예약하고, 준비·검증·게시까지 완료했습니다. |
| `200 OK` | 같은 표시 이름의 테넌트가 이미 활성 상태라 준비를 다시 실행하지 않습니다. |
| `202 Accepted` | 유효 시간이 남은 예약이 아직 준비 중이므로 수명주기를 조회합니다. |
| `409 Conflict` | 같은 테넌트 ID가 다른 표시 이름에 이미 사용되고 있습니다. |
| `500 Internal Server Error` | 준비가 실패했으며 응답에는 안정적인 실패 범주만 포함됩니다. |

```bash
curl -i http://localhost:8080/api/admin/tenants \
  -H 'X-ADMIN-TOKEN: workshop-admin' \
  -H 'Content-Type: application/json' \
  --data '{"tenantId":"acme","displayName":"Acme"}'

curl -i http://localhost:8080/api/tenants/acme \
  -H 'X-ADMIN-TOKEN: workshop-admin'
```

## 데이터베이스 프로필

기본 `h2` 프로필은 별도의 데이터베이스 설치 없이 빠르게 예제를 실행할 때
사용합니다.

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun
```

`postgres` 프로필은 하나의 PostgreSQL 데이터베이스를 공유합니다. 온보딩
수명주기는 `public.tenant_lifecycle`에 저장하고, 테넌트 업무 데이터는
`tenant_<tenant-id>` 스키마로 격리합니다.

| 데이터 | PostgreSQL 위치 |
| --- | --- |
| 온보딩 상태, 유효 시간, 시도 횟수, 실패 범주 | `public.tenant_lifecycle` |
| 테넌트 준비 표식과 이후 업무 테이블 | `tenant_<tenant-id>` |

테넌트 ID의 하이픈은 밑줄로 바꿉니다. 따라서 `clinic-seoul`은
`tenant_clinic_seoul` 스키마를 사용합니다.

```bash
POSTGRES_HOST=localhost \
POSTGRES_PORT=5432 \
POSTGRES_DATABASE=postgres \
POSTGRES_USERNAME=postgres \
POSTGRES_PASSWORD=postgres \
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun \
  --args='--spring.profiles.active=postgres'
```

스키마 생성은 테이블 준비보다 먼저 커밋합니다. 이후 준비나 검증 단계가 실패하면
수명주기 행은 `FAILED`가 되지만, 스키마는 장애 조사와 멱등적인 재시도를 위해
보존합니다. 시작 시 복구 과정에서는 생성 DDL을 실행하지 않습니다. 기존 스키마의
준비 표식을 검증한 뒤 정상인 테넌트만 런타임 레지스트리에 다시 게시합니다.

## 복구 경계

애플리케이션 시작 시 유효 시간이 지난 `PROVISIONING` 행은 `FAILED(RECOVERY)`가 되지만 삭제되지는 않습니다. 저장된 `ACTIVE` 테넌트는 연결을 검증한 뒤에만 프로세스 내 레지스트리에 게시합니다. 영속 상태가 `ACTIVE`라는 사실만으로는 요청을 라우팅할 수 없습니다.

H2 파일 데이터베이스는 빠른 재시작 복구 테스트에 사용합니다. PostgreSQL
프로필은 실제 컨테이너에서 스키마 생성과 격리, 실패 보존, 애플리케이션 문맥
재시작 복구를 검증합니다. 다만 이 예제는 스키마 자동 삭제, 분산 잠금, 외부 비밀
관리, 운영용 인증, 운영 환경에 맞춘 연결 풀, 다중 노드 제어 플레인까지 제공하지는
않습니다.

## 검증

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
```
