# Spring WebFlux 장애 복구형 테넌트 온보딩

[English](./README.md)

이 워크숍은 입문용 `06-tenant-onboarding-spring-webflux` 모듈을 바꾸지 않고, 실행 중 테넌트 생성 흐름을 확장합니다. 영속 소유권, 유효 시간, 시작 시 재조정을 사용해 요청 중단이나 프로세스 재시작 뒤에도 온보딩을 복구하는 방식을 다룹니다.

## 수명주기 레코드가 관리하는 정보

테넌트마다 상태, 시도 횟수, 예약 토큰, 낙관적 버전, 유효 시간, 시각, 안정적인 실패 범주를 가진 영속 레코드가 하나 존재합니다. 원시 예외 메시지, R2DBC URL, 자격 증명은 의도적으로 저장하지 않습니다.

| 상태 | 의미 |
| --- | --- |
| `PROVISIONING` | 하나의 예약 토큰이 제한된 준비 시도를 소유합니다. |
| `ACTIVE` | 준비가 끝나고 런타임 연결 팩토리가 게시되었습니다. |
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

## 복구 경계

애플리케이션 시작 시 유효 시간이 지난 `PROVISIONING` 행은 `FAILED(RECOVERY)`가 되지만 삭제되지는 않습니다. 저장된 `ACTIVE` 테넌트는 연결을 검증한 뒤에만 프로세스 내 레지스트리에 게시합니다. 영속 상태가 `ACTIVE`라는 사실만으로는 요청을 라우팅할 수 없습니다.

H2 파일 데이터베이스는 재시작 복구 테스트에만 유용합니다. 이 모듈은 분산 잠금, PostgreSQL, 인증, 다중 노드 제어 플레인을 보장하는 예제가 아닙니다.

## 검증

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
```
