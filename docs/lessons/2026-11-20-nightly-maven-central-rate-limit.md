# Nightly Maven Central rate limit 완화 교훈

## 상황

같은 `develop` HEAD `e893b88b8b0eaae329020d166379ff68cce3a1e6`에서 Nightly가
연속 실패했다.

- 예약 실행 [#44433602045](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44433602045)은 테스트 전에 `Could not parse POM`과
  `software.amazon.awssdk:bom:2.57.7` 누락으로 의존성 해석에 실패했다.
- 예약 실행 [#44651171083](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44651171083)은 PostgreSQL shard-1과
  shard-3에서 Maven Central의
  `software/amazon/awssdk/bom/2.57.7/bom-2.57.7.pom`을 가져오던 중
  `Received status code 429 from server: Too Many Requests`를 기록했다.
- 같은 HEAD의 이전 실행 [#44216588457](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44216588457)과 이후 수동 재실행
  [#44725741794](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44725741794)은 통과했다.
- 완화 후 `develop` HEAD `c831d08aa36d2963107944bba3acd7363aaacbc6`의 예약 실행
  [#44845456672](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44845456672)은
  `Test / PostgreSQL / shard-1`에서 `jackson-bom:2.23.0` POM 요청 중
  `Received status code 429 from server: Too Many Requests`로 다시 실패했다.
  같은 시각에 `Build & Detekt`, H2, PostgreSQL shard-1이 시작했으며, `max-parallel: 2`는
  test matrix 내부만 제한해 실제 Gradle 의존성 해석 job은 세 개가 겹쳤다.

실패한 실행과 retry-green 실행을 분리해 보면 제품 코드 회귀보다 외부 저장소
요청 폭주에 따른 일시적인 의존성 해석 실패로 분류할 수 있다. 현재 Maven
Central과 Sonatype의 개발 버전 artifact 요청이 `200`을 반환한 것도 이
분류와 일치한다.

## 실패한 가정

Nightly test matrix를 두 개까지 실행하면 Maven Central 요청이 안정적이라고
가정했다. 그러나 `Build & Detekt` job도 `validate-wrapper` 뒤에 test matrix와
동시에 시작하고, matrix 동시 실행과
`build.gradle.kts`의 `cacheChangingModulesFor(0, TimeUnit.SECONDS)` 설정 및
개발 버전 의존성이 겹치면서 같은 외부 메타데이터 요청이 짧은 시간에
집중될 수 있다.

## 결정

`.github/workflows/nightly.yml`의 `test`가 `build` 완료를 기다리도록 하고,
`test.strategy.max-parallel: 1`로 matrix를 직렬화한다. 이로써 Build & Detekt와
test job의 의존성 해석이 겹치지 않고, test matrix에서도 한 번에 하나의
Gradle resolver만 실행된다. 개발 버전 의존성의 최신 여부 계약과 캐시 TTL은
변경하지 않는다.

## 검증 결과

- `actionlint .github/workflows/*.yml`: PASS
- `git diff --check`: PASS
- 변경 HEAD
  `9e40a8be631431e21f7993aab9d83deb93008170`의 Nightly
  [#44731724457](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44731724457): 12/12 job PASS
- 동일 실행 로그에서 `status code.?429`, `429 from server`, Maven Central
  rate-limit 오류 패턴을 다시 검색했지만 일치 항목이 없었다.
- 예약 실행 [#44845456672](https://github.com/bluetape4k/exposed-r2dbc-workshop/actions/runs/44845456672)은
  위 직렬화가 없을 때 `jackson-bom:2.23.0` 요청에서 429가 재발한 사례로
  기록한다. 수정 후에는 변경한 정확한 HEAD의 PR CI와 hosted Nightly를
  별도로 확인한다.

## 재발 방지 규칙

1. Nightly test matrix는 `Build & Detekt` 뒤에 실행하고 `max-parallel: 1`을
   유지한다. matrix나 선행 job을 변경할 때는 변경한 정확한 HEAD의 hosted
   Nightly를 다시 실행한다.
2. 최초 실패와 retry-green 결과를 하나로 합치지 말고, 실패 시 로그에서
   정확한 HTTP status와 의존성 URL을 확인한다.
3. 동시성 제한 후에도 429가 재발할 때만 저장소 미러, 캐시, 재시도 정책을
   별도 근거로 검토한다. 개발 버전 의존성의 최신 여부 계약을 근거 없이
   낮추지 않는다.
