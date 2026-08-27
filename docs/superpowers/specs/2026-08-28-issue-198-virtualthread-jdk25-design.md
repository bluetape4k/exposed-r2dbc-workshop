# Issue #198: JDK25 Virtual Thread 예제 정합화 설계

## 문서 목적

이 문서는 루트 Gradle toolchain이 JDK25인 상태에서
`02-exposed-r2dbc-virtualthreads-basic` 예제가 JDK21 provider를 참조해
테스트를 모두 건너뛰는 문제를 해결하기 위한 설계를 고정한다.

대상 issue: [#198](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/198)

## 문제와 목표

현재 모듈은 `bluetape4k-virtualthread-jdk21`을 test runtime에 추가하고
`@EnabledOnJre(JRE.JAVA_21)`을 사용한다. 저장소의 실제 Java/Kotlin toolchain은
JDK25이고, JDK25에서 모듈 테스트를 실행하면 빌드는 성공하지만 테스트가
실행되지 않는다.

목표는 다음과 같다.

1. 모듈이 중앙 Version Catalog를 통해 `bluetape4k-virtualthread-jdk25:1.12.1`을
   test runtime provider로 사용한다.
2. JDK21 provider가 JDK25 테스트 runtime에 섞이지 않도록 classpath에서 제외한다.
3. 테스트 조건과 KDoc, EN/KO README를 JDK25 기준으로 일치시킨다.
4. ServiceLoader가 JDK25 `StructuredTaskScopeProvider`와
   `VirtualThreadRuntime`을 선택하는지 테스트로 고정한다.
5. 기존 Exposed R2DBC 테스트, TestDB matrix, 모듈 구조와 production 코드는
   변경하지 않는다.

## 범위와 경계

### 포함

- `gradle/libs.versions.toml`의 `bluetape4k-virtualthread-jdk25` alias
- `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/build.gradle.kts`의
  provider dependency와 JDK21 runtime exclusion
- `Ex01_VirtualThreads.kt`의 JDK25 조건, KDoc, ServiceLoader 선택 회귀 테스트
- 같은 모듈의 `README.md`, `README.ko.md` 내 JDK 버전·annotation·실행 안내
- 설계·계획·review·lesson 증적

### 제외

- 새 Gradle module 또는 production Kotlin/Java 소스 추가
- JDK21/JDK25 matrix 또는 JDK21 fallback 지원
- `bluetape4k-dependencies` BOM 자체의 수정
- Exposed transaction/DB lifecycle 동작 변경
- 기존 README diagram과 이미지 asset의 내용·geometry 변경
- PR 생성, merge, release, tag와 같은 외부 side effect (후속 gate에서만 수행)

## 현재 근거

| 근거 | 관찰 결과 | 설계에 미치는 영향 |
|---|---|---|
| Issue #198 live body | root `JavaLanguageVersion.of(25)`, module의 JDK21 runtime/`JRE.JAVA_21`, JDK25에서 4 tests skip | JDK25 단일 실행 계약으로 정합화 |
| `build.gradle.kts:64,69` | root Java/Kotlin toolchain이 25 | toolchain downgrade 금지 |
| `gradle/libs.versions.toml:96` | JDK21 alias만 local catalog에 존재 | JDK25 alias 추가 필요 |
| module `build.gradle.kts:13-14` | JDK21 provider를 `testRuntimeOnly`로 선언 | JDK25 provider로 교체하고 JDK21 transitively 제거 |
| `Ex01_VirtualThreads.kt:67` | `@EnabledOnJre(JRE.JAVA_21)` | `JRE.JAVA_25`로 변경 |
| `bluetape4k-virtualthread-jdk25:1.12.1` JAR | `Jdk25StructuredTaskScopeProvider`, `Jdk25VirtualThreadRuntime` 및 두 ServiceLoader descriptor 존재 | 외부 provider는 이미 배포되어 있어 지금 구현 가능 |
| JDK25 baseline | `:02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true`에서 `SUCCESS: Executed 0 tests` | skip 회귀를 실제 실행 수로 검증 |
| dependency graph | `bluetape4k-coroutines` 경유 JDK21 provider가 test runtime에 들어옴 | `testRuntimeClasspath`에서 JDK21 artifact를 명시적으로 제외 |
| sibling Gradle patterns | `bluetape4k-exposed`의 JDK25 모듈이 JDK21 exclusion과 JDK25 provider를 함께 사용 | 저장소 생태계의 provider isolation 패턴 재사용 |

## 선택지와 결정

### 선택 A — 기존 모듈을 JDK25 provider로 정합화 (채택)

Version Catalog alias를 추가하고 module의 test runtime을 JDK25 provider로
교체한다. `testRuntimeClasspath`에서는 `bluetape4k-virtualthread-jdk21`을
제외한다. 테스트는 API 모듈의 `StructuredTaskScopes.provider()`와
`VirtualThreads.runtime()`을 통해 provider 이름·구체 클래스·지원 여부를
검증한다. 테스트 소스가 provider implementation에 compile-time 결합하지
않도록 dependency는 runtime scope를 유지한다.

이 방식은 현재 root toolchain과 배포된 artifact를 그대로 사용하면서
변경 surface를 기존 모듈로 제한한다. JDK25 provider가 없어지거나 다른
구현으로 대체되면 provider 선택 테스트가 skip 대신 실패한다.

### 선택 B — JDK21/JDK25 provider matrix 유지 (기각)

두 provider를 함께 유지하고 JDK별로 테스트를 분기할 수 있다. 그러나
현재 저장소의 기본 toolchain이 JDK25이고 Issue #198의 목적이 JDK25 provider
정합화이므로, 불필요한 CI matrix와 문서 분기를 만들며 JDK21 provider 혼입
위험을 남긴다.

### 선택 C — Version Catalog를 우회한 dependency 문자열 교체 (기각)

module build script에 직접 Maven coordinate를 적으면 단기 diff는 작다.
하지만 dependency train의 중앙 관리와 versionless alias 규칙을 우회하고
향후 provider version 변경 지점을 분산시킨다.

## 구성 요소와 동작 흐름

```text
Version Catalog alias
        │
        ▼
testRuntimeClasspath: jdk25 provider
        │ (jdk21 provider exclusion)
        ▼
ServiceLoader discovery
        ├─ StructuredTaskScopes.provider()
        │     └─ Jdk25StructuredTaskScopeProvider
        └─ VirtualThreads.runtime()
              └─ Jdk25VirtualThreadRuntime
        │
        ▼
@EnabledOnJre(JRE.JAVA_25)
        │
        ▼
기존 Exposed R2DBC 테스트 + H2/TestDB lifecycle
```

ServiceLoader 선택 테스트는 다음 계약을 확인한다.

- `StructuredTaskScopes.provider().providerName`이 JDK25 provider 이름인
  `jdk25-structured-task-scope`이다.
- 선택된 provider의 구체 클래스가
  `io.bluetape4k.concurrent.virtualthread.jdk25.Jdk25StructuredTaskScopeProvider`
  이거나 동일한 JDK25 provider 계약을 제공한다.
- provider와 runtime의 `isSupported`가 true이다.
- `VirtualThreads.runtime().runtimeName`이 `jdk25`이고 runtime 클래스가
  `Jdk25VirtualThreadRuntime`이다.

테스트는 provider module을 compile-time API로 import하지 않고
`bluetape4k-virtualthread-api`의 public discovery API와 클래스 이름을
검증한다. 이렇게 하면 provider는 runtime 구현으로 남고 catalog/provider
교체 시 실제 ServiceLoader 경계가 검증된다.

## 실패 모드와 대응

| 실패 모드 | 관찰 신호 | 대응 |
|---|---|---|
| JDK25 alias 또는 artifact resolution 실패 | Gradle dependency resolution error | published `1.12.1` coordinate와 catalog alias를 확인하고, 해결 전 구현을 중단한다. |
| JDK21 provider가 runtime에 남음 | provider class가 JDK21로 선택되거나 classpath에 두 provider가 존재 | `testRuntimeClasspath.exclude`를 유지하고 dependency insight로 resolved graph를 재검증한다. |
| ServiceLoader descriptor 누락/지원 불가 | provider discovery exception, `isSupported == false`, 또는 의도하지 않은 skip | 테스트를 실패시키고 provider artifact/Java runtime을 수정한다. fallback으로 숨기지 않는다. |
| JDK25 조건이 문서·소스와 불일치 | JRE annotation 또는 README에 `JDK 21`/`JAVA_21` 잔존 | source·KDoc·EN/KO README를 같은 변경에서 갱신하고 문자열 audit을 재실행한다. |
| JDK25 StructuredTaskScope ABI 불일치 | test compile/runtime `NoSuchMethodError`, `UnsupportedClassVersionError` | provider와 active JDK를 `1.12.1`/JDK25로 고정하고, 해당 artifact가 해결될 때까지 PR을 차단한다. |

기존 MariaDB nested-transaction 조건 skip은 이 설계의 대상이 아니다.
그 조건은 현재 DB capability 계약이므로 그대로 유지하고, JDK25 조건으로
인해 발생하는 의도하지 않은 skip과 구분한다.

## 호환성과 migration

- root Java/Kotlin toolchain 25를 유지한다.
- 이 변경은 테스트 fixture와 문서의 실행 전제만 바꾸며 production API나
  저장 데이터 schema를 변경하지 않는다.
- JDK21에서 이 예제 module을 실행하는 호환 matrix는 제공하지 않는다.
  JDK25 provider artifact가 JDK25 runtime을 요구하므로 README에 JDK25를
  명시한다.
- `bluetape4k-dependencies:1.4.0`과 provider `1.12.1`의 versionless
  catalog 규칙을 유지한다.
- rollback은 하나의 feature commit(또는 연속된 Lore commits)을 revert해
  JDK21 alias/dependency/조건/문서로 되돌리는 방식이다. rollback 후에는
  JDK25에서 다시 skip 상태가 되는 것이 예상되므로 Issue #198 완료 조건은
  충족하지 않는다.

## 검증 설계

### RED/GREEN

1. provider 선택 테스트를 먼저 추가하고 JDK25 provider alias가 없는 상태에서
   compile/resolution 실패 또는 기대 provider 불일치를 관찰한다(RED).
2. catalog alias, dependency isolation, JDK25 annotation을 구현한다.
3. 같은 테스트에서 provider/runtime 선택 PASS와 기존 네 개 테스트의 실제
   실행을 확인한다(GREEN).

### 필수 검증

- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true`
  - JDK25에서 provider 선택 테스트와 기존 테스트가 실행되어야 한다.
  - 의도하지 않은 JDK condition skip과 provider 누락 오류가 없어야 한다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:test`
  - 전체 configured DB matrix의 기존 동작과 MariaDB capability skip을
    구분해 확인한다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:compileTestKotlin`
  - Kotlin source 및 API 사용을 확인한다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:detekt`
  - Kotlin style/static diagnostics를 확인한다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:koverXmlReport`
  - 기존 Kover 경로가 유지되는지 확인한다(실제 task 이름은
    `tasks --all`로 확인 후 실행한다).
- `./gradlew projects`
  - module registration이 변하지 않았음을 확인한다.
- `git diff --check`
  - 공백/patch 오류가 없어야 한다.
- catalog/dependency insight
  - JDK25 provider가 resolved되고 JDK21 provider가 test runtime에서
    제외됐음을 확인한다.
- README locale audit
  - `README.md`와 `README.ko.md`의 JDK 전제, annotation, 실행 명령,
    경고가 서로 대응하고 기존 diagram 링크는 손상되지 않아야 한다.

Testcontainers/real DB 검증은 기존 모듈의 full test가 요구하는 범위에서만
직렬로 실행한다. 새 DB backend나 container를 추가하지 않는다.

## 수용 기준과 DoD

| ID | 수용 기준 | 증명 |
|---|---|---|
| AC-01 | JDK25 provider alias와 dependency가 중앙 catalog를 사용한다 | catalog read-back, Gradle resolution |
| AC-02 | JDK21 provider가 JDK25 test runtime에 섞이지 않는다 | resolved runtime graph와 exclusion 확인 |
| AC-03 | `@EnabledOnJre(JRE.JAVA_25)` 조건에서 테스트가 실제 실행된다 | fast test XML/log의 executed/ skipped count |
| AC-04 | JDK25 `StructuredTaskScopeProvider`와 `VirtualThreadRuntime`이 ServiceLoader로 선택된다 | provider/runtime 선택 테스트 |
| AC-05 | Exposed R2DBC 테스트 동작과 기존 DB capability skip이 유지된다 | module full test |
| AC-06 | EN/KO README와 KDoc이 JDK25 실행 전제와 일치한다 | source/README parity audit |
| AC-07 | module registration/Kover/diff/Kotlin checks가 통과한다 | `projects`, Kover, detekt/compile, `git diff --check` |

완료 시 다음을 모두 만족해야 한다.

- 변경 파일이 설계 범위 안에 있다.
- P0/P1 review finding이 없다.
- provider 누락이나 의도하지 않은 skip이 없다.
- CI가 exact PR head에서 통과한다.
- PR body에 Korean metadata와 마지막 `## DoD Status`가 있다.
- merge는 별도의 fresh approval 후 `--rebase --match-head-commit`으로만
  수행한다.

## 다이어그램 범위 결정

이번 변경은 기존 diagram asset의 의미나 geometry를 바꾸지 않고 README의
텍스트와 코드 annotation만 갱신한다. 따라서 `bluetape-diagram` 시각 QA는
적용 대상이 아니며, 최종 checklist에는 “diagram/asset 변경 없음”이라는
구체적 N/A 근거를 남긴다.

## 설계 결론

선택 A를 채택한다. 외부 provider가 실제로 배포되어 있고 현재 실패가
JDK21 조건 skip과 provider 혼입에서 재현되므로, 새 모듈이나 provider
workaround 없이 기존 예제를 JDK25 기준으로 고정하는 것이 가장 작은
검증 가능한 변경이다.
