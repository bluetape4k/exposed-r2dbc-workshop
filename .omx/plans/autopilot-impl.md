# Autopilot Implementation Plan

1. `00-shared`의 `withDb` 기본 경로에서 불필요한 재연결을 제거하고 회귀 테스트를 추가한다.
2. `09-spring/07-spring-suspended-cache`에서
   - `SCAN` 기반 전체 삭제로 교체한다.
   - cache manager가 Redis 연결을 닫도록 보강한다.
   - 단위/통합 테스트를 추가한다.
3. `10-multi-tenant/03-multitenant-spring-webflux`에서
   - 잘못된 tenant 요청을 `400 Bad Request`로 매핑한다.
   - pool max size 기본값을 보수적으로 조정하고 계산 테스트를 추가한다.
   - 불필요한 `CoroutineScope` 위임을 제거한다.
   - mapper/tenant 관련 public API KDoc을 보강한다.
4. 루트 README와 `11-high-performance/README.md`를 입문 허브 중심으로 재구성한다.
5. 모듈별 테스트 후 전체 `./gradlew test`를 실행해 regression을 확인한다.
