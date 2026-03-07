# Task Statement
`exposed-r2dbc-workshop` 저장소 전체 모듈에 대해 코드 리뷰 및 개선, 테스트 보강, public API KDoc 보강, `README.md` 최적화, 전체 regression test 수행

# Desired Outcome
- 성능/안정성 관점의 실질적인 개선 사항을 반영한다.
- 누락되거나 취약한 테스트를 보강한다.
- 공개 클래스/인터페이스/확장 함수의 KDoc을 한글로 보강한다.
- 루트 및 주요 README를 현재 저장소 구조에 맞게 정리한다.
- 전체 회귀 테스트를 실행하고 결과를 검증한다.

# Known Facts / Evidence
- 루트 `AGENTS.md` 기준으로 Kotlin/Coroutines 고급 수준의 리뷰와 KDoc 보강이 요구된다.
- `src/main/kotlin`을 가진 주요 모듈은 `00-shared`, `01-spring-boot`, `09-spring`, `10-multi-tenant`, `11-high-performance`, `buildSrc`이다.
- 다수의 다른 모듈은 예제/테스트 중심 구조이며 `src/test/kotlin`만 가진다.
- 루트 README는 학습 경로와 모듈 설명이 길게 나열되어 있으나, 실행 방법/검증 범위/유지보수 관점 정보가 상대적으로 약하다.
- 현재 Git 상태에서 `.omx/`가 untracked 상태다.

# Constraints
- 사용자 변경사항을 되돌리지 않는다.
- 네트워크는 제한적이며 로컬 정보와 빌드/테스트 결과 중심으로 판단한다.
- 공개 API KDoc은 한국어로 작성하고 기존 포맷을 유지한다.
- 변경은 실제 결함/문서 공백/테스트 공백이 확인된 영역에 집중한다.

# Unknowns / Open Questions
- 전체 테스트 기준선에서 현재 실패가 존재하는지 여부
- 공개 API 중 KDoc 누락 범위
- README 최적화가 필요한 모듈별 수준
- 다중 DB/Testcontainers 테스트가 로컬 환경에서 모두 통과하는지 여부

# Likely Codebase Touchpoints
- `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/README.md`
- `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/00-shared/exposed-r2dbc-shared/src/main/kotlin`
- `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/09-spring/07-spring-suspended-cache/src/main/kotlin`
- `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/11-high-performance/03-routing-datasource/src/main/kotlin`
- `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/**/src/test/kotlin`
