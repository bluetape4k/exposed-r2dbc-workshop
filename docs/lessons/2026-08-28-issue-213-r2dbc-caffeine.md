# Issue #213 Exposed R2DBC Caffeine 예제 작업 교훈

## 맥락

Chapter 11에 `bluetape4k-exposed-r2dbc-caffeine:1.12.1` provider를 사용하는
Ktor R2DBC 예제를 새 sibling으로 추가했다. 목표는 provider의 세 가지
`CacheWriteMode`와 애플리케이션이 소유하는 R2DBC pool 종료 순서를 실행 가능한
테스트로 고정하는 것이었다.

## 결정

- provider의 `AbstractR2dbcCaffeineRepository` mapping 계약만 구현하고,
  애플리케이션은 `ProductRecord`를 DB 행·캐시 값·JSON 응답에 그대로 사용했다.
- `R2dbcCaffeineResources`가 bounded H2 `ConnectionPool`,
  `R2dbcDatabase`, repository를 소유한다. 종료는 repository final flush,
  Exposed manager unregister, pool dispose 순서로 고정하고 기존 default DB를
  복원한다.
- `READ_ONLY`, `WRITE_THROUGH`, `WRITE_BEHIND`를 동일한 PUT API로 검증하되,
  write-behind는 cache publish와 DB flush를 별도 상태로 관찰한다.
- provider에 있는 `R2dbcCaffeineSnapshotCache`는 별도 계약이므로 이 예제에서
  구현하지 않았다. 자동 retry, crash durability, exactly-once 효과도 주장하지
  않는다.

## 구현 중 발견

처음에는 subclass constructor에서 provider의 `config`를 다시 `override val`로
선언했다. provider base initializer가 이미 같은 설정을 읽기 때문에 초기화
순서가 깨졌고, 실제 테스트에서 실패했다. subclass는 constructor 인자를
그대로 base constructor에 전달하고 provider가 소유한 `config`를 재정의하지
않도록 고쳤다.

write-behind flush가 실패하면 provider는 실패한 batch를 queue에 남긴다. 따라서
실패 테스트의 기준은 `queueDepth == 0`이 아니라 `lastFlushError != null`,
`workerState == FAILED`, 실패 batch가 보존된다는 사실이다. DB의 기존 값과
optimistic cache 값의 차이를 직접 읽고 invalidate한 뒤 확인해야 한다.

## 결과와 검증

- RED 단계에서 production symbol이 없다는 컴파일 실패를 확인한 뒤 GREEN으로
  전환했다.
- `./gradlew :07-cache-strategies-r2dbc-caffeine:test -PuseDB=H2
  --no-configuration-cache --console=plain`에서 application 6개와 lifecycle
  4개, 총 10개 테스트가 순차적으로 통과했다.
- `./gradlew projects`에서 `:07-cache-strategies-r2dbc-caffeine`가 발견되고,
  중앙 catalog의 versionless provider alias가 컴파일에 사용됐다.
- 영문/한글 README와 아키텍처·시퀀스 SVG/PNG를 함께 만들었다. PNG 4개는
  CairoSVG scale 2로 렌더링했고, 모두 불투명하다. 아키텍처는 3200x2000,
  bbox occupancy 0.908, 여백 58px이며 시퀀스는 3680x3000, bbox occupancy
  0.926, 여백 62px이다. connector, endpoint, arrowhead, geometry,
  mixed-corner, sequence-style, asset-pair 감사가 모두 통과했다.

## 다음 작업에서 지킬 guard

provider adapter subclass를 만들 때는 base initializer가 읽는 설정을
constructor에서 재정의하지 않는다. write-behind 테스트는 성공 drain과
실패 batch 보존을 분리하고, `validateConsistency()`의 queue/worker/error
상태를 직접 검증한다. 종료 코드는 repository → Exposed manager → pool
순서를 유지하며, 이 순서를 바꾸는 리팩터링에는 lifecycle 회귀 테스트를
먼저 추가한다.
