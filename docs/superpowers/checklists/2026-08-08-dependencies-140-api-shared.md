# dependencies 1.4.0 API 및 shared 통합 체크리스트

이 체크리스트는 `bluetape4k-dependencies:1.4.0`의 실제 해석 결과를 예제에
반영하고, 공통 R2DBC 설정을 shared로 통합한 뒤, 승격 후보를 중복·유효성
검증하여 GitHub 이슈로 남기는 작업의 증거 장부입니다. 각 행은 확인한
명령/파일과 실패 시 중단 또는 복구 경로를 함께 기록합니다.

| ID | 분류 | 조치 | 증거 | 실패 시 |
|---|---|---|---|---|
| CL-01 | 필수 | 승인된 계획과 Type-A 범위를 고정 | 사용자 승인 `승인`, workflow A 승인/시작 receipt | 승인 범위 밖 변경 금지 |
| CL-02 | 필수 | 원본 worktree의 기존 변경을 보존 | 기준선 status에서 이미지 77개 dirty 확인 | 원본 worktree 접근 중단, 격리 worktree 사용 |
| CL-03 | 필수 | 격리 worktree와 전용 workflow owner를 사용 | `.worktrees/dependencies-140-api-shared`, runtime owner handle | 원본 worktree에서 코드 변경 금지 |
| CL-04 | 필수 | 1.4.0 BOM의 실제 해석 버전과 API를 확인 | Gradle dependency graph, 1.12.1 JAR/API 확인 | API 변경 착수 보류, source/JAR 재검증 |
| CL-05 | 필수 | shared 후보가 도메인 중립인지와 소비 모듈을 확인 | 중복 경로 목록, 호출자 검색, 모듈 의존성 | 도메인 로직은 각 예제에 유지 |
| CL-06 | 필수 | 수정 전 회귀 계약을 고정 | shared `WithTablesTest`, 옵션/풀 DSL 테스트 계획 | 테스트 추가 후 RED 확인 전 구현 금지 |
| CL-07 | 필수 | 승격 후보의 live 중복·유효성을 확인 | 대상 저장소 issue 검색, published source/JAR, 재현 테스트 | 중복이면 이슈 생성하지 않음 |
| CL-08 | 필수 | 외부 issue 생성 전 최신 duplicate scan과 권한 확인 | `gh issue list`, 대상 repo/labels/head 재확인 | issue mutation 중단 |
| CG-01 | 필수 | 작업 디렉터리와 기준 head를 기록 | `git status --short`, `git rev-parse HEAD`, `git worktree list` | head/dirty 범위 불명확 시 중단 |
| CG-02 | 필수 | 승인 receipt와 mutation scope를 확인 | `bluetape-flow.py verify`, `mutation-check` | runtime gate 복구 후 재시도 |
| CG-03 | 필수 | source/API 연구 근거를 보존 | Gradle graph, official/published source 좌표 | 근거 없는 API 치환 금지 |
| CG-04 | 필수 | TDD RED → GREEN → REFACTOR 순서 준수 | 테스트 실패/통과 로그와 변경 diff | 구현을 되돌리고 테스트부터 보강 |
| CG-05 | 조건부 | shared 변경의 모든 소비자를 컴파일·테스트 | affected Gradle module list와 test logs | 실패 모듈만 좁혀 수정, 범위 확장 금지 |
| CG-06 | 조건부 | 전체 예제의 수동 R2DBC builder 잔여를 점검 | `rg` 결과와 예외 목록 | 의도적 URL/테넌트 생성기는 문서화 |
| CG-07 | 필수 | GitHub issue를 한국어로 생성하고 read-back | issue URL/number/title/body read-back | body 불일치 시 수정 또는 close 판단 |
| CG-08 | 필수 | 최종 diff·테스트·dirty 보존·workflow evidence를 확인 | `git diff --check`, Gradle 결과, completion-check | `PENDING`으로 보고하고 미완료 항목 명시 |
| CG-09 | N/A | PR 생성·리뷰 요청 | 사용자 요청에 target repo/base/head 없음 | PR mutation을 수행하지 않음 |
| CG-10 | N/A | merge/auto-merge/tag/release/publication | 사용자 요청 범위에 없음 | release mutation을 수행하지 않음 |
| CG-11 | N/A | PR exact-head mergeability gate | PR 자체를 만들지 않음 | N/A 근거 유지 |
| CG-12 | N/A | PR CI/review thread gate | PR 자체를 만들지 않음 | N/A 근거 유지 |
| CG-13 | N/A | release publication gate | release를 요청하지 않음 | N/A 근거 유지 |
| CG-14 | 조건부 | 다중 DB 통합 검증 | fast DB 후 선택된 비-H2 결과 | 비-H2는 미검증으로 명시 |
| CG-15 | 필수 | 대상 issue의 중복·유효성 재검증 | issue 직전 live API 조회 | mutation 취소 |
| CG-16 | 필수 | 변경 파일 write scope와 owner lane을 확인 | NUL-safe status와 lane-complete evidence | 범위 밖 파일은 복원하지 말고 보고 |
| CG-17 | 필수 | 한국어 public metadata와 Lore commit 규칙 준수 | issue/commit 본문 점검 | push/commit 보류 |
| CG-18 | 필수 | DoD와 unchecked item을 사용자에게 보고 | 최종 검증 receipt와 표 형태 보고 | 상태를 `PENDING`으로 유지 |

## 진행 상태

- 완료: CL-01~CL-08, CG-01~CG-08, CG-14~CG-15, CG-17의 사전 조사·구현·검증.
- 완료: shared `R2dbcConnectionOptions`와 옵션 회귀 테스트, Spring/Ktor/테넌트 예제의
  `connectionFactoryOptionsOf`·`connectionPoolOf`·`connectionFactoryOf` 전환.
- 완료: `./gradlew test -PuseFastDB=true --continue`와
  `./gradlew build -x test -x detekt --continue` 성공, builder 잔여 검색과
  `git diff --check` 성공.
- 완료: Maven Central live POM/JAR/source 확인, upstream 전체 이슈 중복 재검색,
  [bluetape4k-exposed #625](https://github.com/bluetape4k/bluetape4k-exposed/issues/625)
  한국어 생성 및 read-back.
- N/A: CG-09~CG-13 (PR·merge·release/publication을 요청하지 않음).
- 진행 중: CG-16 runtime lane-complete/write-scope receipt와 CG-18 최종 DoD receipt.
