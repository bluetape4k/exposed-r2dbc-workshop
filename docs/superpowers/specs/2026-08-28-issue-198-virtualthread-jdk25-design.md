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
- 루트 `README.md`, `README.ko.md`의 Coroutines/Virtual Threads 학습 경로 링크
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
| provider classfile/module metadata | classfile `major=69`, `minor=0`, Gradle module metadata `org.gradle.jvm.version=25`, local JAR SHA-256 `aab053515aba60ce238dc49d2bccc4c2d05f640f3ce8bf4746c680b616a964a9` | JDK25 정규 classfile이며 `--enable-preview`를 추가하지 않음; published artifact read-back 근거를 보존 |
| JDK25 baseline | `:02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true`에서 `SUCCESS: Executed 0 tests` | skip 회귀를 실제 실행 수로 검증 |
| dependency graph | `bluetape4k-coroutines` 경유 JDK21 provider가 test runtime에 들어옴 | `testRuntimeClasspath`에서 JDK21 artifact를 명시적으로 제외 |
| sibling Gradle patterns | `bluetape4k-exposed`의 JDK25 모듈이 JDK21 exclusion과 JDK25 provider를 함께 사용 | 저장소 생태계의 provider isolation 패턴 재사용 |

## 선택지와 결정

### 선택 A — 기존 모듈을 JDK25 provider로 정합화 (채택)

Version Catalog에 versionless alias를 추가하고 module의 test runtime을 JDK25
provider로 교체한다. provider 버전 `1.12.1`은 imported
`bluetape4k-dependencies:1.4.0` BOM이 결정하며 local catalog에는 버전을
중복 기입하지 않는다. 전체 `testRuntimeClasspath` configuration에서
`bluetape4k-virtualthread-jdk21`을 제외한다. 테스트는 API 모듈의
`StructuredTaskScopes.provider()`와 `VirtualThreads.runtime()`을 통해 public
provider 이름·runtime 이름·지원 여부를 검증하고, `ServiceLoader`에 허용된
provider가 정확히 하나인지 확인한다. 내부 provider 구현 class 이름은 public
계약으로 고정하지 않으며, 정확한 artifact identity는 resolved dependency graph와
service descriptor로 검증한다. 테스트 소스가 provider implementation에
compile-time 결합하지 않도록 dependency는 runtime scope를 유지한다.

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
        │     └─ JDK25 structured-task provider
        └─ VirtualThreads.runtime()
              └─ JDK25 virtual-thread runtime
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
- provider와 runtime의 `isSupported`가 true이다.
- `VirtualThreads.runtime().runtimeName`이 `jdk25`이고 JDK25 runtime 계약을
  제공한다.
- `ServiceLoader.load(StructuredTaskScopeProvider::class.java)`와
  `ServiceLoader.load(VirtualThreadRuntime::class.java)` 각각에 허용된
  JDK25 provider가 정확히 하나만 등록된다.
- 내부 구현 class 이름은 assertion 대상이 아니다. JDK25 artifact 선택은
  resolved `testRuntimeClasspath`에서 `bluetape4k-virtualthread-jdk25`가
  정확히 하나이고 JDK21 artifact가 0개인지로 증명한다.

테스트는 provider module을 compile-time API로 import하지 않고
`bluetape4k-virtualthread-api`의 public discovery API를 검증한다. 이렇게
하면 provider는 runtime 구현으로 남고 catalog/provider 교체 시 실제
ServiceLoader 경계가 검증된다. provider의 child failure 전파와 scope close
후 child 종료는 500ms bounded timeout을 사용하는 public
`StructuredTaskScopes.failFast` smoke test로 별도 검증한다.

## 실패 모드와 대응

| 실패 모드 | 관찰 신호 | 대응 |
|---|---|---|
| JDK25 alias 또는 artifact resolution 실패 | Gradle dependency resolution error | published `1.12.1` coordinate와 catalog alias를 확인하고, 해결 전 구현을 중단한다. |
| JDK21 provider가 runtime에 남음 | provider class가 JDK21로 선택되거나 classpath에 두 provider가 존재 | `testRuntimeClasspath.exclude`를 유지하고 dependency insight로 resolved graph를 재검증한다. |
| ServiceLoader descriptor 누락/지원 불가 | provider discovery exception, `isSupported == false`, 또는 의도하지 않은 skip | 테스트를 실패시키고 provider artifact/Java runtime을 수정한다. fallback으로 숨기지 않는다. |
| ServiceLoader 중복/비허용 provider | 허용 provider 수가 1이 아니거나 unknown/JDK21 descriptor가 발견됨 | `ServiceLoader` enumeration과 runtime graph를 실패시키고 JDK25 provider 단일성을 복구한다. |
| JDK25 조건이 문서·소스와 불일치 | JRE annotation 또는 README에 `JDK 21`/`JAVA_21` 잔존 | source·KDoc·EN/KO README를 같은 변경에서 갱신하고 문자열 audit을 재실행한다. |
| JDK25 StructuredTaskScope ABI/preview 불일치 | test compile/runtime `NoSuchMethodError`, `UnsupportedClassVersionError`, preview 요구 | provider와 active JDK를 `1.12.1`/JDK25로 고정하고 classfile `major=69`, `minor=0` 및 module metadata를 확인한다. `--enable-preview`가 필요한 artifact면 PR을 차단하고, 정규 classfile이면 preview flag를 추가하지 않는다. |

기존 MariaDB nested-transaction 조건 skip은 이 설계의 대상이 아니다.
그 조건은 현재 DB capability 계약이므로 그대로 유지하고, JDK25 조건으로
인해 발생하는 의도하지 않은 skip과 구분한다.

## 호환성과 migration

### 2.0.0-SNAPSHOT API namespace migration

현재 workshop catalog는 `bluetape4k-dependencies:2.0.0-SNAPSHOT`을 소비한다.
이 train의 `bluetape4k-virtualthread-api`는 public discovery 계약을
`io.bluetape4k.concurrent.virtualthread.api` namespace로 제공한다. 따라서
기존 #198 구현의 다음 네 import는 snapshot API 계약에 맞춰야 한다.

```kotlin
import io.bluetape4k.concurrent.virtualthread.api.StructuredTaskScopeProvider
import io.bluetape4k.concurrent.virtualthread.api.StructuredTaskScopes
import io.bluetape4k.concurrent.virtualthread.api.VirtualThreadRuntime
import io.bluetape4k.concurrent.virtualthread.api.VirtualThreads
```

`newVT`와 Exposed R2DBC transaction helper는 core의 기존
`io.bluetape4k.concurrent.virtualthread` namespace를 유지하므로 함께 바꾸지
않는다. provider implementation class를 직접 import하거나 구 API를 되살리는
compatibility layer도 추가하지 않는다. migration의 성공 기준은
`compileTestKotlin` 성공, fast test `5/5/0`, module `check` 성공이며, public
ServiceLoader singleton/name/support 및 bounded scope assertions는 그대로
유지한다.

- root Java/Kotlin toolchain 25를 유지한다.
- 이 변경은 테스트 fixture와 문서의 실행 전제만 바꾸며 production API나
  저장 데이터 schema를 변경하지 않는다.
- JDK21에서 이 예제 module을 실행하는 호환 matrix는 제공하지 않는다.
  JDK25 provider artifact가 JDK25 runtime을 요구하므로 README에 JDK25를
  명시한다.
- local `libs.versions.toml`에는 versionless alias만 두고,
  `bluetape4k-dependencies:1.4.0` BOM이 provider `1.12.1`을 결정하는
  책임 경계를 유지한다.
- rollback은 하나의 feature commit(또는 연속된 Lore commits)을 revert해
  JDK21 alias/dependency/조건/문서로 되돌리는 기술적 경로다. rollback 후
  JDK25에서 다시 skip 상태가 되는 것은 **비수용 상태**이며 Issue #198
  완료 조건이 아니다. 실행 수/skip gate는 rollback 대상에서 분리하고,
  기본 복구 전략은 JDK25 forward-fix로 한다.

## 검증 설계

### RED/GREEN

1. provider 선택 테스트와 bounded fail-fast smoke test를 먼저 추가하고 JDK25 provider alias가 없는 상태에서
   compile/resolution 실패 또는 기대 provider 불일치를 관찰한다(RED).
2. catalog alias, dependency isolation, JDK25 annotation을 구현한다.
3. 같은 테스트에서 provider/runtime 선택 PASS와 기존 네 개 테스트의 실제
   실행을 확인한다(GREEN).

### 필수 검증

- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true`
  - JDK25에서 provider 선택/smoke test 1개와 기존 H2 parameterized test
    4개가 실행되어 총 5개, skip 0이어야 한다.
  - 이 실행은 Gradle execution gate가 XML의 `executed >= 5`와 `skipped == 0`을
    강제해야 하며, 실패 시 build를 성공으로 끝내지 않는다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:test`
  - 기본 `H2,POSTGRESQL,MYSQL_V8` 실행은 총 13개, skip 0이어야 한다.
    MariaDB를 명시한 별도 실행에서는 MariaDB capability skip만 allowlist로
    허용하고 다른 skip은 실패시킨다.
- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution -PuseFastDB=true`
  - clean JDK25 환경에서 test report의 executed/skipped count를 자동
    검증한다. 의도적으로 `JAVA_21` 조건을 되돌린 fixture는 gate가 실패해야 한다.
  - task는 `test`에 의존하고 JUnit XML의 `tests - skipped`를 executed로
    합산한다. `useFastDB=true`에서는 5개 이상 및 skip 0, 기본 dialect
    (`H2,POSTGRESQL,MYSQL_V8`)에서는 13개 이상 및 skip 0을 요구한다.
    MariaDB를 명시한 실행만 해당 capability skip 이름을 allowlist로 인정한다.
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
  - `./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencies --configuration testRuntimeClasspath`
    결과에 JDK25 provider가 정확히 1개 나타난다.
  - `./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencyInsight --dependency bluetape4k-virtualthread-jdk21 --configuration testRuntimeClasspath`
    결과에 JDK21 provider가 resolved되지 않는다.
- provider artifact read-back
  - JAR classfile `major=69`, `minor=0`, module metadata
    `org.gradle.jvm.version=25`, 두 service descriptor와 SHA-256을 확인한다.
    현재 repository에 Gradle dependency verification metadata가 없으므로 이
    SHA/POM read-back은 이번 변경의 artifact identity 증거로 보존하고,
    repository-wide verification policy 도입은 별도 후속 범위로 둔다.
- README locale audit
  - root/module `README.md`와 `README.ko.md`의 JDK 전제, annotation, 실행
    명령, provider 경고가 서로 대응하고 모든 링크가 존재해야 한다.

Testcontainers/real DB 검증은 기존 모듈의 full test가 요구하는 범위에서만
직렬로 실행한다. 새 DB backend나 container를 추가하지 않는다.

## 수용 기준과 DoD

| ID | 수용 기준 | 증명 |
|---|---|---|
| AC-01 | JDK25 provider alias와 dependency가 중앙 catalog를 사용한다 | catalog read-back, Gradle resolution |
| AC-02 | 전체 JDK25 test runtime에 JDK21 provider가 섞이지 않고 JDK25 provider가 정확히 하나다 | 전체 configuration exclusion, dependency graph와 service descriptor 확인 |
| AC-03 | `@EnabledOnJre(JRE.JAVA_25)` 조건에서 테스트가 실제 실행되고 0 tests green이 차단된다 | fast 5개/full 13개 expected count와 Gradle execution gate |
| AC-04 | public discovery API가 JDK25 provider/runtime 이름·지원 여부와 ServiceLoader 단일성을 증명한다 | provider/runtime 선택 테스트와 enumeration |
| AC-05 | Exposed R2DBC 테스트 동작과 기존 DB capability skip이 유지된다 | module full test |
| AC-06 | bounded structured-scope smoke가 child failure 전파와 close 후 child 종료를 증명한다 | 500ms timeout smoke test |
| AC-07 | EN/KO root/module README와 KDoc이 JDK25 실행 전제·provider 기대값과 일치한다 | source/README parity 및 링크 audit |
| AC-08 | module registration/Kover/diff/Kotlin checks가 통과한다 | `projects`, Kover, detekt/compile, `git diff --check` |

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
