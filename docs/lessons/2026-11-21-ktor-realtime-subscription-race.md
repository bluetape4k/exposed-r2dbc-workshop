# Ktor realtime 구독 준비 경쟁 조건 교훈

## 상황

Nightly [#44881352472](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44881352472)의 H2 job에서
`KtorProductionIntegrationApplicationTest.websocket realtime endpoint streams live event after subscription()`가
`Timed out waiting for 5000 ms`로 실패했다. PostgreSQL, MySQL 8, MariaDB와
빌드·Coverage job은 성공했으므로 데이터베이스 호환성이나 Maven Central
rate limit과는 다른 Ktor lifecycle 문제로 분류했다.

## 실패한 가정

WebSocket route가 `launch { realtimeHub.live().collect { ... } }`를 호출하면
publisher가 실행되기 전에 collector가 항상 `MutableSharedFlow` 구독을 등록할
것이라고 가정했다. 실제로는 route가 `incoming`을 처리하는 동안 client의
`POST /production/outbox/publish`가 먼저 실행될 수 있다. 이때
`subscriptionCount`가 아직 0이면 `KtorRealtimeHub.deliver`는 이벤트를
정상 수락한 것으로 반환하면서 live stream에는 이벤트를 전달하지 않는다.
이벤트 원본은 DB에 남지만, 현재 WebSocket 연결은 5초 안에 받을 이벤트가
없어 timeout으로 끝난다.

## 결정

WebSocket route가 live collector를 시작한 뒤 `KtorRealtimeHub.awaitSubscriber()`로
실제 구독 등록을 확인하고 `incoming` 처리를 시작하도록 한다. 이 대기 함수는
`subscriptionCount`가 1 이상이 될 때까지 suspend하므로 임의의 sleep이나
timeout 연장으로 race를 가리지 않는다. 연결이 취소되면 부모 coroutine의
cancellation이 대기를 중단하고 기존 `finally` 블록이 collector를 정리한다.

## 결과

- `KtorRealtimeHub`가 collector 등록을 기다리는 lifecycle 경계를 제공한다.
- WebSocket route는 live event publish 전에 구독 준비를 보장한다.
- 구독 준비 대기 회귀 테스트와 실제 Ktor WebSocket 통합 테스트를 함께 유지한다.
- 이전 Nightly의 Maven Central 429 완화는 별도 workflow 변경으로 유지하며,
  이번 수정에서 dependency cache나 rate-limit 정책은 변경하지 않는다.

## 검증

- 수정 전 RED: `KtorProductionIntegrationApplicationTest.realtime hub waits for a live collector before accepting publish`가
  `awaitSubscriber` 미정의로 컴파일 실패했다.
- 수정 후 GREEN: 두 targeted 테스트가 `2 passing`으로 통과했다.
- 후속 검증은 affected module 전체 테스트, Detekt, `git diff --check`, exact-head
  PR CI, hosted Nightly 순서로 수행한다.

## 향후 지침

1. Flow 기반 WebSocket route에서 publisher를 허용하기 전에 protocol-level
   readiness 또는 실제 collector 등록 신호를 기다린다.
2. `MutableSharedFlow`의 `tryEmit` 반환값은 subscriber가 아직 없을 때도
   성공할 수 있으므로, `true`를 live client 수신 증거로 해석하지 않는다.
3. retry-green 결과만으로 lifecycle race를 무시하지 말고, 실패한 exact-head
   테스트의 collector 등록·publish 순서를 먼저 확인한다.
