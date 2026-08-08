# dependencies 1.4.0 API 및 shared 통합 실행 계획

## 목표와 완료 조건

`bluetape4k-dependencies:1.4.0`의 실제 해석 결과가 제공하는
`bluetape4k-r2dbc:1.12.1` API를 기준으로 R2DBC 예제를 정렬한다. DB 연결
옵션의 중복은 `exposed-r2dbc-shared`로 옮기고, 각 예제의 풀 크기·생명주기·테넌트
정책은 유지한다. 구현 완료는 다음을 모두 만족하는 상태다.

- shared 옵션 계약과 기존 `withTables` 취소 계약의 회귀 테스트가 통과한다.
- Spring/Ktor 및 테넌트 예제의 직접 builder 호출이 실제 DSL 또는 URL 파서로
  전환되고, 영향받은 모듈이 컴파일·테스트된다.
- 후보 라이브러리 API의 공개성, Maven Central POM/JAR 유효성, live GitHub
  issue 중복을 새로 확인한다.
- 중복이 아니고 재사용 가치가 있는 결함만 한국어 GitHub 이슈로 기록하고
  read-back한다.
- 원본 worktree의 기존 dirty 이미지와 격리 worktree 밖의 파일을 변경하지 않는다.

## 순서

1. **RED 계약 고정**
   - shared 테스트에 H2/MySQL/PostgreSQL 옵션 필드와 URL 변환 계약을 추가한다.
   - 구현 전 대상 테스트를 실행해 실패 증거를 남긴다.
   - 기존 `WithTablesTest` cancellation/suppressed-exception 회귀 테스트를
     baseline으로 재실행한다.
2. **shared API 구현**
   - `R2dbcServerCredentials`와 DB별 `ConnectionFactoryOptions` 팩토리를
     추가한다.
   - 팩토리 내부에서 `connectionFactoryOptionsOf`만 사용하고, framework 또는
     도메인 타입을 shared에 끌어오지 않는다.
   - GREEN 테스트 후 API 이름·옵션 기본값을 단순화한다.
3. **예제 전환**
   - 다섯 Spring 설정과 Ktor/테넌트 설정을 파일별로 전환한다.
   - 풀 구성은 `connectionPoolOf`/`r2dbcConnectionPool`으로 바꾸되,
     모듈별 pool tuning과 tenant URL 정책을 보존한다.
   - 영향 모듈에만 `bluetape4k-r2dbc` 및 필요한 SPI compile dependency를
     명시한다.
   - 잔여 `ConnectionPoolConfiguration.builder`와
     `ConnectionFactoryOptions.builder/parse`를 검색해 의도적 예외만 남긴다.
4. **단계별 검증**
   - shared 단위 테스트와 영향 모듈 compile/test를 실행한다.
   - `./gradlew test -PuseFastDB=true --continue`로 전체 회귀를 실행하고,
     필요하면 비-H2 DB 검증 결과를 별도 기록한다.
   - `git diff --check`, 소스 API import 검색, 원본 worktree dirty 보존을
     확인한다.
5. **승격 후보 판정과 이슈 기록**
   - published `bluetape4k-exposed-r2dbc-tests:1.12.1`의 `withTables`와 local
     cancellation-safe 구현을 비교한다.
   - Maven Central POM/JAR HTTP 200 및 공개 source/JAR 근거를 확인한다.
   - `bluetape4k/bluetape4k-exposed`의 live issue를 직전 재검색한다.
   - 중복이 아닐 때만 한국어 이슈를 생성하고 `gh issue view`로 본문을
     read-back한다. 중복이면 이슈를 만들지 않고 근거를 남긴다.
6. **마감**
   - checklist A-01~A-12와 CG 행을 실제 명령/로그로 갱신한다.
   - verifier 관점으로 diff, 테스트, 의도적 잔여 builder, public metadata,
     write scope를 재검토한다.
   - 커밋이 필요할 경우 Lore trailer를 포함한 한국어 메시지를 사용하며,
     PR/merge/release는 범위 밖으로 유지한다.

## 위험과 완화

- **라이브러리 API drift**: 캐시 JAR의 `javap` 결과와 Gradle resolved graph를
  함께 확인하고, source-compatible한 DSL만 사용한다.
- **풀 semantics 변경**: 공통 팩토리는 옵션만 만들고 pool tuning은 호출자에
  남겨 기존 max size, warm-up, eviction, validation 계약을 보존한다.
- **취소 예외 회귀**: published fixture로 전체 교체하지 않고 local
  `withTables`와 회귀 테스트를 유지한다.
- **이슈 중복**: 후보 issue 생성 직전에 live 검색을 다시 수행하며, workshop의
  과거 #54는 upstream library issue와 별도라는 점을 본문에 명시한다.
- **dirty surface 오염**: 모든 코드 변경은 격리 worktree에서만 수행하고,
  원본 worktree의 baseline status를 최종 비교한다.

## 검증 명령

```bash
./gradlew :exposed-r2dbc-shared:test --tests '*R2dbcConnectionOptionsTest'
./gradlew :exposed-r2dbc-shared:test --tests '*WithTablesTest'
./gradlew test -PuseFastDB=true --continue
./gradlew build -x test -x detekt --continue
git diff --check
```

명령이 환경 제약으로 실행되지 않으면 실패 로그와 대체 검증을 checklist와
최종 DoD에 남긴다.
