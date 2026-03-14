# AGENTS.md

이 저장소에서 작업하는 에이전트는 이 문서를 우선 참고한다. 이 프로젝트는 Exposed + R2DBC 기반의 Kotlin 학습/예제 워크샵이며, 모든 코드는 Kotlin으로 작성한다.

## 프로젝트 개요

- Kotlin Exposed R2DBC 학습 워크샵이다.
- JetBrains Exposed 프레임워크를 R2DBC 환경에서 사용하는 예제를 모듈별로 구성한다.
- Java 코드는 기본 대상이 아니며, Kotlin 구현과 코루틴 기반 비동기 흐름을 우선한다.

## 프로젝트 구조

- 루트 빌드 파일: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `buildSrc/`
- 공통 테스트 유틸: `00-shared/exposed-r2dbc-shared`
- 시나리오 모듈:
  - `01-spring-boot`
  - `03-exposed-r2dbc-basic`
  - `04-exposed-r2dbc-ddl`
  - `05-exposed-r2dbc-dml`
  - `06-advanced`
  - `07-jpa-convert`
  - `08-r2dbc-coroutines`
  - `09-spring`
  - `10-multi-tenant`
  - `11-high-performance`
- 일반적인 모듈 레이아웃:
  - `src/main/kotlin`
  - `src/test/kotlin`
  - `src/main/resources`
  - `src/test/resources`

모듈 이름은 `settings.gradle.kts`의 설정을 따른다. 하위 디렉토리 이름이 Gradle 프로젝트 이름으로 매핑되는 경우가 많으므로 실제 태스크 실행 전 프로젝트 경로를 확인한다.

## 빌드/테스트 명령

루트에서 Gradle Wrapper를 사용한다.

- 전체 빌드: `./gradlew build`
- 전체 테스트: `./gradlew test`
- 클린 빌드: `./gradlew clean build`
- 정적 분석: `./gradlew detekt`
- 특정 모듈 테스트 예시:
  - `./gradlew :exposed-r2dbc-shared:test`
  - `./gradlew :spring-webflux-exposed:test`
- 특정 테스트 클래스 실행 예시:
  - `./gradlew :01-dml:test --tests "exposed.r2dbc.examples.dml.Ex01_Select"`
- 저장소 상태 요약: `./bin/repo-status`
- diff 요약: `./bin/repo-diff`
- Gradle/테스트 출력 요약: `./bin/repo-test-summary -- ./gradlew <task>`

## 아키텍처 및 핵심 패턴

### 의존성 관리

- 라이브러리 버전은 우선 `buildSrc/src/main/kotlin/Libs.kt`에서 확인한다.
- 새 의존성을 추가할 때는 중앙 정의 후 각 모듈에서 참조하는 방식을 우선한다.

### 공통 테스트 인프라

`00-shared/exposed-r2dbc-shared`는 테스트 기반 모듈이다. 다음 컴포넌트를 먼저 확인한다.

- `AbstractR2dbcExposedTest`: 테스트 기본 클래스, UTC 타임존 고정, dialect 활성화 헬퍼 제공
- `TestDB`: 지원 DB enum
- `Containers`: Testcontainers 기반 DB 컨테이너 싱글턴
- `withDb(testDB) { }`: DB별 직렬화와 트랜잭션 컨텍스트를 포함한 실행 헬퍼
- `withTables(testDB, *tables) { }`: 테이블 생성/정리 포함 헬퍼

### Exposed R2DBC 사용 규칙

- Exposed v1 패키지를 사용한다: `org.jetbrains.exposed.v1.core`, `org.jetbrains.exposed.v1.r2dbc`
- 모든 DB 접근은 `suspendTransaction` 또는 `withDb`/`withTables` 블록 안에서 수행한다.
- `selectAll()` 등 Flow 기반 API는 수집 시점과 terminal operator를 의식해서 사용한다.
- 코루틴 코드에서는 cancellation 전파, dispatcher 경계, 숨은 blocking 호출 유입 여부를 우선 점검한다.
- 데이터 접근 코드에서는 transaction 경계, connection lifecycle, retry/isolation 가정을 함께 검토한다.

### 로깅

- 일반 클래스: `companion object: KLogging()`
- 코루틴 환경: `companion object: KLoggingChannel()`
- 로깅은 `log.debug { "..." }` 형태를 유지한다.

## 테스트 패턴

기존 테스트 스타일을 먼저 따른다.

```kotlin
class Ex01_Select: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `select example`(testDB: TestDB) = runTest {
        withTables(testDB, MyTable) {
            // test body
        }
    }
}
```

- `@ParameterizedTest` + `@MethodSource(ENABLE_DIALECTS_METHOD)` 패턴을 우선 고려한다.
- 테스트는 결정적이어야 하며 공유 상태를 피한다.
- 동작 변경 시 관련 테스트를 추가하거나 보강한다.
- 특히 코루틴 경계, 트랜잭션 경계, DB dialect 차이는 회귀 테스트로 확인한다.
- 개발 중에는 모듈 단위 테스트를 먼저 실행하고, 최종적으로 관련 Gradle 검증 근거를 남긴다.

## 개발 규칙

- Kotlin 2.3, Java 21 기준을 유지한다.
- Kotlin 공식 스타일과 4-space indentation을 따른다.
- 이름 규칙:
  - 클래스/객체: `PascalCase`
  - 함수/프로퍼티: `camelCase`
  - 상수: `UPPER_SNAKE_CASE`
- 예제/테스트 클래스는 `ExNN_Description` 또는 `*Test` 패턴을 존중한다.
- 공개 클래스, 인터페이스, 확장함수에는 KDoc을 작성한다. 이 저장소에서는 한글 KDoc을 선호한다.
- 기존 포맷과 구조를 유지하고, 최소 diff 원칙으로 수정한다.
- 불필요한 리팩터링이나 구조 변경은 피한다.

## 작업 방식

- 구현 전에 기존 코드와 테스트 패턴을 먼저 확인한다.
- 가능하면 `intellij-index` 계열 도구를 우선 사용해 정의 찾기, 참조 찾기, 안전한 리팩터링을 수행한다.
- 단순 검색은 `rg`를 우선 사용한다.
- 사용자의 기존 변경사항은 임의로 되돌리지 않는다.
- 리뷰 요청일 때는 요약보다 findings를 먼저 제시하고, 성능/안정성/테스트 누락 여부를 우선 본다.
- 최신 외부 API나 라이브러리 계약이 개입되면 공식 문서 또는 1차 소스를 먼저 확인한다.

## 환경 요구사항

- JDK 21+
- Gradle Wrapper 사용 권장
- Docker/Testcontainers 실행 가능 환경
- 기본 테스트 타임존은 UTC 기준임을 가정한다

## 커밋/PR 규칙

- Conventional Commit 스타일을 사용한다:
  - `feat:`
  - `fix:`
  - `refactor:`
  - `test:`
  - `docs:`
  - `chore:`
- 커밋은 하나의 응집된 변경만 담는다.
- PR 또는 작업 보고에는 목적, 영향 모듈, 실행한 테스트/검증 명령, 필요한 설정 사항을 함께 남긴다.
