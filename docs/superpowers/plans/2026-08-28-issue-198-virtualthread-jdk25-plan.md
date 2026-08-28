# JDK25 Virtual Thread Provider 정합화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** JDK25 toolchain에서 `02-exposed-r2dbc-virtualthreads-basic` 테스트가 JDK25 provider를 단일하게 선택하고 실제로 실행되도록 dependency, ServiceLoader 회귀 검증, 실행 수 gate, 문서를 정합화한다.

**Architecture:** 기존 모듈과 공개 virtual-thread API를 유지하고 Version Catalog의 versionless alias와 BOM이 결정하는 provider만 test runtime에 연결한다. `testRuntimeClasspath`에서 JDK21 provider를 전체 configuration 기준으로 제외한 뒤, 하나의 provider smoke test가 public discovery API와 bounded structured-scope lifecycle을 검증한다. Gradle verification task가 JUnit XML 실행 수와 MariaDB capability skip allowlist를 확인해 JDK 조건으로 인한 `0 tests executed` green을 차단한다.

**Tech Stack:** Kotlin 2.4.0, JDK 25, Gradle Kotlin DSL, JUnit 5, Exposed R2DBC, bluetape4k virtual-thread API/provider `1.12.1`, Testcontainers, Markdown README/KDoc.

---

## 실행 경계와 선행 조건

- 대상 issue: [#198](https://github.com/bluetape4k/exposed-r2dbc-workshop/issues/198)
- 저장소: `bluetape4k/exposed-r2dbc-workshop`
- 기준: `origin/develop` SHA `d975bd24fafbb3e998b8cb1735652f7ff6f9b75d`
- 실행 worktree: `/Users/debop/work/bluetape4k/exposed-r2dbc-workshop/.worktrees/feat-issue-198-virtualthread-jdk25`
- branch: `feat/issue-198-virtualthread-jdk25`
- workflow run: `20260827T185619Z-32b9ae70`
- 승인 상태: 설계와 작성 사양의 사용자 `승인` 완료. 이 계획은 코드 변경 전 별도 사용자 계획 승인을 받아야 한다.
- 외부 경계: 이 계획과 구현에서는 PR 생성, push, merge, tag, release를 수행하지 않는다. PR은 최종 pre-PR DoD 이후 별도 권한 gate에서 생성하고, merge는 exact head에 대한 새 `승인` 뒤 `gh pr merge --rebase --match-head-commit`으로만 수행한다.
- Kotlin-only 범위: production Kotlin/Java 소스와 새 모듈을 추가하지 않는다. 변경은 기존 테스트 fixture, Gradle 설정, catalog, README, KDoc, 증적 문서로 제한한다.
- 실행 stop condition: plan review의 P0/P1이 0이고 사용자가 계획을 승인하기 전에는 구현 task를 시작하지 않는다. 구현 후에는 fast/default/명시적 MariaDB gate, dependency graph, compile/detekt/Kover, 문서 parity, final review가 모두 증명될 때까지 PR 단계로 이동하지 않는다.

### 구현 commit과 rollback 경계

- 구현 중간에 다음 세 commit 경계를 유지한다. 각 commit은 Lore trailer를 포함하고,
  해당 경계의 targeted validation을 통과한 뒤에만 생성한다.
  1. `Issue #198 JDK25 provider를 실제 테스트 경로에 연결`: JDK25
     annotation/smoke, Version Catalog alias, JDK21 exclusion, JDK25 provider
     dependency를 포함한다.
  2. `Issue #198 0 tests 실행 수 gate를 fail-closed로 고정`: 실행 수 XML
     검증 task와 그 negative-path 검증만 포함한다.
  3. `Issue #198 JDK25 문서와 lesson을 실행 결과에 맞춰 정합화`: README,
     KDoc, lesson, review/checklist 갱신을 포함한다.
- provider/test commit을 되돌릴 때도 execution-gate commit은 유지한다.
  selective revert 후 JDK25에서 `verifyVirtualThreadTestExecution`을 다시
  실행하고, `0 tests executed` 또는 provider 부재를 gate failure로 관찰해야
  한다. gate까지 되돌리는 전체 rollback은 원인 분석용으로만 허용하며,
  복구 상태는 다시 fail-closed gate를 포함해야 한다.

## 파일 책임 지도

| 경로 | 책임 | 변경 범위 |
|---|---|---|
| `gradle/libs.versions.toml` | 중앙 Version Catalog | `bluetape4k-virtualthread-jdk25` versionless alias 추가 |
| `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/build.gradle.kts` | 모듈 test runtime과 실행 수 gate | JDK21 exclusion, JDK25 provider, `verifyVirtualThreadTestExecution` task 추가 |
| `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/src/test/kotlin/exposed/r2dbc/examples/virtualthreads/Ex01_VirtualThreads.kt` | JDK25 조건과 provider/lifecycle 회귀 테스트 | `JRE.JAVA_25`, public ServiceLoader smoke, bounded close 검증, MariaDB assumption message, Korean KDoc |
| `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md` | 모듈 영어 독자 문서 | JDK25/provider 계약, 실행 gate, capability skip, 예제 annotation 갱신 |
| `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.ko.md` | 모듈 한국어 독자 문서 | 영어 README와 같은 사실·명령·링크를 한국어 기술 문체로 갱신 |
| `README.md` | 루트 영어 학습 경로/모듈 맵 | Virtual Threads 모듈 직접 링크와 JDK25 전제 추가 |
| `README.ko.md` | 루트 한국어 학습 경로/모듈 맵 | 영어 루트 README와 대응하는 링크·전제 추가 |
| `docs/lessons/2026-08-28-issue-198-virtualthread-jdk25.md` | durable lesson | 구현·검증 결과를 근거와 future guard로 기록 |
| `docs/review/issue-198-plan-review.md` | 통합 계획 review | 6관점 결과, severity 정규화, P0/P1 수렴, SPW 결과 기록 |
| `docs/superpowers/checklists/2026-08-28-issue-198-type-a.md` | Type A lifecycle 증적 | 계획 review/승인, 구현·검증 결과를 단계별로 갱신 |

다음 경로는 조사와 검증만 수행하며 변경하지 않는다: `settings.gradle.kts`,
root `build.gradle.kts`, `00-shared/exposed-r2dbc-shared`, 기존 diagram PNG와
source, `CHANGELOG.md`. 모듈은 이미 `settings.gradle.kts`에 등록되어 있고,
이번 issue는 release-facing 변경이 아니므로 `CHANGELOG.md`는 N/A로 증명한다.

## 의존 순서

```text
계획 승인
  -> RED provider smoke
  -> catalog/dependency isolation + execution gate
  -> GREEN provider/DB tests
  -> README/KDoc parity
  -> compile/test/gate/detekt/Kover/dependency/artifact 검증
  -> lesson + final review
  -> fresh pre-PR DoD
```

각 구현 task는 이전 task의 명령 결과를 읽은 뒤 진행한다. Testcontainers와
실제 DB를 사용하는 Gradle invocation은 worktree 간에 동시에 실행하지 않는다.

## Task 1: 구현 직전 기준선과 artifact identity 재확인

**Files:**
- Read: `AGENTS.md`, `/Users/debop/work/bluetape4k/.github/docs/workspace/AGENTS.md`, `/Users/debop/.codex/AGENTS.md`
- Read: `docs/superpowers/specs/2026-08-28-issue-198-virtualthread-jdk25-design.md`
- Read: `docs/review/issue-198-spec-review.md`
- Read: `gradle/libs.versions.toml`, module `build.gradle.kts`, `Ex01_VirtualThreads.kt`, root/module READMEs
- Modify: `docs/superpowers/checklists/2026-08-28-issue-198-type-a.md` only for fresh evidence

- [ ] **Step 1: 실행 경계와 worktree를 확인한다.**

```bash
pwd
git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/develop
git worktree list --porcelain
```

Expected evidence: 현재 경로가 Issue #198 전용 worktree이고 branch가
`feat/issue-198-virtualthread-jdk25`이며, unrelated 변경과 untracked artifact가
없다. root `develop` worktree와 Issue #205 worktree는 보존한다.

- [ ] **Step 2: JDK25와 provider artifact를 읽어 dependency 결정을 재확인한다.**

```bash
java -version
./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencies \
  --configuration testRuntimeClasspath --no-daemon --console=plain
JDK25_JAR="/Users/debop/.gradle/caches/modules-2/files-2.1/io.github.bluetape4k/bluetape4k-virtualthread-jdk25/1.12.1/6ed90ada6fa00481ec92222c9cb573cf0028cf6a/bluetape4k-virtualthread-jdk25-1.12.1.jar"
sha256sum "$JDK25_JAR"
jar tf "$JDK25_JAR" | rg 'META-INF/services|Jdk25Structured|Jdk25Virtual'
```

Expected evidence: active JDK 25, baseline runtime graph의 현재 JDK21
provider 혼입, JDK25 JAR SHA-256
`aab053515aba60ce238dc49d2bccc4c2d05f640f3ce8bf4746c680b616a964a9`, 두
ServiceLoader descriptor가 확인된다. 이 단계에서는 code/catalog를 편집하지
않는다.

- [ ] **Step 3: 기준선 실행 수를 저장한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
  -PuseFastDB=true --no-daemon --console=plain
```

Expected evidence: 현재 `@EnabledOnJre(JRE.JAVA_21)` 조건 때문에 JDK25에서
빌드는 성공하지만 `Executed 0 tests`가 관찰된다. 결과가 다르면 raw test XML과
현재 annotation을 함께 읽고 plan review에 drift를 기록한다.

## Task 2: provider 선택과 lifecycle을 먼저 고정하는 RED 테스트

**Files:**
- Modify: `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/src/test/kotlin/exposed/r2dbc/examples/virtualthreads/Ex01_VirtualThreads.kt`
- Test: the same file, one additional `@Test` method; existing four `@ParameterizedTest` methods keep their SQL, transaction, retry, and lifecycle behavior while gaining the explicit XML display-name contract below

- [ ] **Step 1: public API와 lifecycle 검증 import를 추가하고 JDK25 조건을 RED 테스트 준비로 바꾼다.**

추가 import는 다음 public API와 JDK 표준 타입만 사용한다. provider implementation
class를 compile-time import하지 않는다.

```kotlin
import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopeProvider
import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopes
import io.bluetape4k.concurrent.virtualthread.VirtualThreadRuntime
import io.bluetape4k.concurrent.virtualthread.VirtualThreads
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Instant
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
```

클래스 annotation은 RED 실행에서도 테스트가 실제로 선택되도록
`@EnabledOnJre(JRE.JAVA_25)`로 바꾼다. KDoc의 JDK21 설명은 이 task의 마지막
문서 변경에서 JDK25 사실과 함께 갱신하며, 이 단계에서는 다른 예제 동작을
리팩터링하지 않는다.

- [ ] **Step 2: provider 단일성과 bounded fail-fast close를 한 개의 smoke test로 작성한다.**

추가할 테스트는 다음 계약을 그대로 따른다. 하나의 `@Test`로 묶어 fast 실행
수를 `1 provider smoke + 4 dialect parameterized = 5`로 고정한다. 기존 네
parameterized test에는 다음 명시적 display-name 계약을 적용해 JUnit XML의
identity가 dialect와 method display name을 포함하도록 한다.

```kotlin
@ParameterizedTest(name = "{displayName} {0}")
```

`{displayName}`과 `{0}`의 조합은 각 기존 메서드에 동일하게 적용하며 SQL,
transaction, retry, `TestDB` lifecycle은 바꾸지 않는다. 따라서 gate는
`<method display name> <dialect>`의 exact name multiset을 확인할 수 있다.

```kotlin
@Test
@Timeout(value = 2, unit = TimeUnit.SECONDS)
fun `JDK25 provider와 runtime을 선택하고 structured scope를 닫는다`() {
    val providers = ServiceLoader.load(StructuredTaskScopeProvider::class.java).toList()
    providers shouldHaveSize 1
    val provider = providers.single()
    provider.providerName shouldBeEqualTo "jdk25-structured-task-scope"
    provider.isSupported shouldBeEqualTo true
    StructuredTaskScopes.providerName() shouldBeEqualTo "jdk25-structured-task-scope"

    val runtimes = ServiceLoader.load(VirtualThreadRuntime::class.java).toList()
    runtimes shouldHaveSize 1
    val runtime = runtimes.single()
    runtime.runtimeName shouldBeEqualTo "jdk25"
    runtime.isSupported shouldBeEqualTo true
    VirtualThreads.runtimeName() shouldBeEqualTo "jdk25"

    val startupBudget = 1L
    val childStopped = AtomicBoolean(false)
    val childrenStarted = CountDownLatch(2)
    val failure = runCatching {
        StructuredTaskScopes.failFast(
            "issue-198-provider-smoke",
            VirtualThreads.threadFactory("issue-198-provider-smoke"),
        ) { scope ->
            scope.fork {
                childrenStarted.countDown()
                childrenStarted.await(startupBudget, TimeUnit.SECONDS) shouldBeEqualTo true
                throw IllegalStateException("intentional child failure")
            }
            scope.fork {
                childrenStarted.countDown()
                try {
                    childrenStarted.await(startupBudget, TimeUnit.SECONDS) shouldBeEqualTo true
                    Thread.sleep(5_000)
                } finally {
                    childStopped.set(true)
                }
            }
            childrenStarted.await(startupBudget, TimeUnit.SECONDS) shouldBeEqualTo true
            scope.joinUntil(Instant.now().plusMillis(500))
            scope.throwIfFailed()
        }
    }.exceptionOrNull()

    val childFailure = requireNotNull(failure)
    childFailure shouldBeInstanceOf IllegalStateException::class
    (childFailure as IllegalStateException).message shouldBeEqualTo "intentional child failure"
    childStopped.get() shouldBeEqualTo true
}
```

`startupBudget`은 두 child와 main block이 함께 barrier를 통과해야 하는 하나의
CI-safe startup budget이다. `@Timeout(2s)`와 `500ms` join deadline이 최종
liveness를 제한하므로 이 값은 latency SLO가 아니다. `CountDownLatch` 두 개의
child가 실제로 시작된 뒤 실패하도록 보장하므로 `childStopped`는 scheduler
순서에 좌우되지 않는다. `runCatching`은
provider 구현에 따라 `joinUntil` 또는 `throwIfFailed`에서 발생하는 실제
failure를 보존하되, 이 provider의 public contract가 전파하는 정확한
`IllegalStateException` type/message를 검사한다. 특정 JDK internal exception
class는 assert하지 않는다. 내부 `500ms` deadline은 structured scope join의
bounded contract다. 실패하면 close/interruption 순서를 조사하고 테스트를
통과시키기 위해 timeout을 무작정 늘리지 않는다.

- [ ] **Step 3: 의도한 RED를 실행한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
  -PuseFastDB=true --tests \
  'exposed.r2dbc.examples.virtualthreads.Ex01_VirtualThreads.JDK25 provider와 runtime을 선택하고 structured scope를 닫는다' \
  --no-daemon --console=plain
```

Expected evidence: 기존 JDK21 provider/classpath 상태에서는 provider 이름,
지원 여부, ServiceLoader 개수 중 하나 이상이 JDK25 계약과 불일치해 test가
PASS하지 않는다(또는 현재 Gradle/JUnit display-name 선택이 지원되지 않으면
같은 모듈 test를 실행해 해당 assertion failure를 확보한다). RED 결과와 예외
type를 읽고 Task 3 구현 후 동일 assertion이 GREEN인지 비교한다. RED를
`0 tests`로 끝내지 않는다.

## Task 3: catalog, provider isolation, 실행 수 gate의 최소 구현

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/build.gradle.kts`
- Test: Task 2의 `Ex01_VirtualThreads.kt`를 GREEN으로 재실행

- [ ] **Step 1: Version Catalog에 versionless JDK25 alias를 추가한다.**

`bluetape4k-virtualthread-jdk21` 인접 위치에 다음 한 줄을 추가한다.

```toml
bluetape4k-virtualthread-jdk25 = { module = "io.github.bluetape4k:bluetape4k-virtualthread-jdk25" }
```

버전은 catalog에 적지 않는다. root subproject가 가져오는
`bluetape4k-dependencies:1.4.0` BOM이 provider `1.12.1`을 결정하는 단일
authority다.

- [ ] **Step 2: JDK21 provider를 전체 test runtime에서 제외하고 JDK25 provider를 연결한다.**

기존 `configurations` 블록과 provider dependency를 다음 형태로 바꾼다.

```kotlin
configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
    testRuntimeClasspath {
        exclude(
            group = "io.github.bluetape4k",
            module = "bluetape4k-virtualthread-jdk21",
        )
    }
}

// JDK 25 Virtual Thread provider는 imported bluetape4k-dependencies BOM이 버전을 결정한다.
testRuntimeOnly(libs.bluetape4k.virtualthread.jdk25)
```

기존 `testRuntimeOnly(libs.bluetape4k.virtualthread.jdk21)`는 제거한다. 다른
dependency와 `implementation(libs.bluetape4k.coroutines)`는 바꾸지 않는다.

execution gate를 아직 추가하지 않은 상태에서 다음 targeted GREEN을 먼저
실행한다.

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
  -PuseFastDB=true --no-daemon --console=plain
```

provider smoke와 H2 parameterized test가 `5 executed / 0 skipped`인지 읽은
뒤, catalog·module dependency·JDK25 test 변경만 첫 번째 Lore commit으로
고정한다. 이 commit은 실행 수 gate 파일 변경을 포함하지 않는다.

- [ ] **Step 3: JUnit XML을 fail-closed로 검사하는 Gradle task를 추가한다.**

`build.gradle.kts`에 다음 책임을 가진 `verifyVirtualThreadTestExecution` task를
등록한다.

```kotlin
import org.gradle.api.tasks.testing.Test
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

val virtualThreadTest = tasks.named<Test>("test")

tasks.register("verifyVirtualThreadTestExecution") {
    dependsOn(virtualThreadTest)
    doLast {
        val reportDir = layout.buildDirectory.dir("test-results/test").get().asFile
        val reports = reportDir.listFiles { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            ?.sortedBy { it.name }
            .orEmpty()
        require(reports.isNotEmpty()) { "JUnit XML report가 없어 virtual-thread 실행 수를 검증할 수 없습니다." }

        val factory = DocumentBuilderFactory.newDefaultInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }

        var total = 0
        var skipped = 0
        var failures = 0
        var errors = 0
        data class SkippedCase(val name: String, val type: String, val message: String)
        val skippedCases = mutableListOf<SkippedCase>()
        val caseIdentities = mutableListOf<Pair<String, String>>()
        reports.forEach { report ->
            val suite = factory.newDocumentBuilder().parse(report).documentElement
            require(suite.tagName == "testsuite") {
                "JUnit XML root가 testsuite가 아닙니다: ${report.name}"
            }
            val declaredTests = suite.getAttribute("tests").toIntOrNull()
                ?: error("JUnit XML tests 속성이 정수가 아닙니다: ${report.name}")
            val cases = suite.getElementsByTagName("testcase")
            require(cases.length == declaredTests) {
                "JUnit XML testcase 수가 선언값과 다릅니다: ${report.name}"
            }
            total += declaredTests
            skipped += suite.getAttribute("skipped").toInt()
            failures += suite.getAttribute("failures").toInt()
            errors += suite.getAttribute("errors").toInt()
            for (index in 0 until cases.length) {
                val testCase = cases.item(index) as Element
                caseIdentities += testCase.getAttribute("classname") to testCase.getAttribute("name")
                val skippedNodes = testCase.getElementsByTagName("skipped")
                if (skippedNodes.length > 0) {
                    val skippedNode = skippedNodes.item(0)
                    val type = skippedNode.attributes?.getNamedItem("type")?.nodeValue.orEmpty()
                    val message = skippedNode.attributes?.getNamedItem("message")?.nodeValue.orEmpty()
                    skippedCases += SkippedCase(testCase.getAttribute("name"), type, message)
                }
            }
        }

        val knownDialects = setOf("H2", "H2_MYSQL", "H2_PSQL", "H2_MARIADB", "H2_ORACLE", "H2_SQLSERVER", "MARIADB", "MYSQL_V5", "MYSQL_V8", "POSTGRESQL")
        val requested = project.providers.gradleProperty("useDB").orNull
        val requestedFastDb = project.providers.gradleProperty("useFastDB").orNull
        val useFastDb = when (requestedFastDb) {
            null -> false
            "true" -> true
            "false" -> false
            else -> error("useFastDB는 true 또는 false여야 실행 수를 검증할 수 있습니다.")
        }
        val selectedDialects = if (requested != null) {
            val requestedTokens = requested.split(',').map { it.trim() }
            require(requestedTokens.all { token ->
                token.isNotEmpty() && knownDialects.any { it.equals(token, ignoreCase = true) }
            }) {
                "useDB에 알 수 없거나 빈 dialect token이 포함되어 있어 실행 수를 검증할 수 없습니다."
            }
            requestedTokens.map { token ->
                knownDialects.first { it.equals(token, ignoreCase = true) }
            }.toSet()
        } else if (useFastDb) {
            setOf("H2")
        } else {
            setOf("H2", "POSTGRESQL", "MYSQL_V8")
        }

        val allowedMariaDbSkips = selectedDialects.count { it == "MARIADB" || it == "H2_MARIADB" }
        val minimumTotal = 1 + selectedDialects.size * 4
        val expectedSkipped = allowedMariaDbSkips
        val minimumExecuted = minimumTotal - expectedSkipped
        val testClassName = "exposed.r2dbc.examples.virtualthreads.Ex01_VirtualThreads"
        val parameterizedDisplayNames = listOf(
            "virtual threads 를 이용하여 순차 작업 수행하기",
            "중첩된 virtual thread 용 트랜잭션을 async로 실행",
            "다수의 비동기 작업을 수행 후 대기",
            "virtual threads 환경에서 조건 조회",
        )
        val providerSmokeName = "JDK25 provider와 runtime을 선택하고 structured scope를 닫는다"
        val expectedIdentities = selectedDialects.flatMap { dialect ->
            parameterizedDisplayNames.map { displayName -> testClassName to "$displayName $dialect" }
        } + (testClassName to providerSmokeName)
        val actualIdentityCounts = caseIdentities.groupingBy { it }.eachCount()
        val expectedIdentityCounts = expectedIdentities.groupingBy { it }.eachCount()
        require(expectedIdentityCounts.all { (identity, expectedCount) ->
            actualIdentityCounts.getOrDefault(identity, 0) >= expectedCount
        }) {
            "JUnit XML에 기대한 virtual-thread testcase identity가 없습니다."
        }
        val nestedTransactionDisplayName = "중첩된 virtual thread 용 트랜잭션을 async로 실행"
        val expectedSkippedNames = selectedDialects
            .filter { it == "MARIADB" || it == "H2_MARIADB" }
            .map { "$nestedTransactionDisplayName $it" }
        require(skippedCases.map { it.name }.sorted() == expectedSkippedNames.sorted()) {
            "허용된 MariaDB capability skip 수가 다릅니다: expected=$expectedSkipped actual=${skippedCases.size}"
        }
        val expectedSkipType = "org.opentest4j.TestAbortedException"
        val expectedSkipMessage =
            "org.opentest4j.TestAbortedException: Assumption failed: MariaDB-compatible nested transactions are not supported"
        require(skippedCases.all { skippedCase ->
            skippedCase.name in expectedSkippedNames &&
                skippedCase.type == expectedSkipType &&
                skippedCase.message == expectedSkipMessage
        }) {
            "허용되지 않은 skipped testcase type/message가 발견되었습니다."
        }
        require(failures == 0 && errors == 0) {
            "테스트 failure/error가 있어 실행 수 gate를 통과할 수 없습니다: failures=$failures errors=$errors"
        }
        require(total >= minimumTotal && skipped == expectedSkipped && total - skipped >= minimumExecuted) {
            "virtual-thread test 실행 수가 최소 계약과 다릅니다: total=$total executed=${total - skipped} skipped=$skipped minimumTotal=$minimumTotal minimumExecuted=$minimumExecuted expectedSkipped=$expectedSkipped"
        }

        // 환경 변수, system property, credential 값은 출력하지 않고 경로와 집계값만 출력한다.
        logger.lifecycle("virtual-thread test execution verified: reports=${reports.size} total=$total executed=${total - skipped} skipped=$skipped")
    }
}
```

실제 구현에서는 사용하지 않는 import를 남기지 않는다. 명시적으로 전달된
`useDB` 값은 대소문자만 정규화하고, unknown token·빈 token을 H2로 축소하지
않고 즉시 실패시킨다. 따라서 `-PuseDB=TYPO`, `-PuseDB=H2,` 및
`-PuseDB=`는 모두 execution gate failure이며 잘못된 matrix가 green으로
남지 않는다. 위 parser의 secure XML
feature 설정이 JDK/Gradle XML parser에서 지원되지 않으면 task를 실패시키고,
외부 DTD/schema 접근 차단과 hostile XML negative fixture를 통과하는 동등한
표준 설정으로 교체한 뒤에만 재실행한다. 보안 feature를 생략한 채 진행하지
않는다. parser는 `useDB`/`useFastDB` 값을 읽지만 그 값이나
`System.getProperties()`/환경 변수 전체를 log하지 않는다. 명시적 MariaDB
선택이 아닌 경우 모든 skip은 허용하지 않으며, 명시적 선택에서도 중첩
transaction testcase 외의 skip과 exact JUnit abort type/message가 아닌 skip은
실패한다. JUnit XML의 `<skipped>`에는 `type="org.opentest4j.TestAbortedException"`
및 `message="org.opentest4j.TestAbortedException: Assumption failed: MariaDB-compatible nested transactions are not supported"`가
직렬화된다는 실제 report 형식을 고정한다.
`useFastDB`도 지정된 경우 정확히 `true` 또는 `false`만 허용하며, `maybe` 같은
malformed 값은 기본 matrix로 전환하지 않고 즉시 실패시킨다.

실행 수 gate는 승인된 design과 동일한 minimum contract를 사용한다. 현재
소스 기준 fast `5`, default `13`은 기대 evidence의 정확한 관찰값이지만,
향후 의도적으로 테스트를 추가하면 gate는 `minimumTotal`/`minimumExecuted`
이상으로 통과할 수 있다. 기존 테스트를 제거해 minimum 아래로 내려가거나
허용되지 않은 skip이 생기면 실패하며, 추가·삭제 시 README/checklist의 관찰값과
identity 목록을 함께 갱신한다.

- [ ] **Step 4: dependency와 provider smoke GREEN을 확인한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:compileTestKotlin \
  --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
  -PuseFastDB=true --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseFastDB=true --no-daemon --console=plain
```

Expected evidence: JDK25 provider smoke 1개와 H2 parameterized 4개가 실행되어
`total=5 executed=5 skipped=0 failures=0 errors=0`이다. provider 이름/runtime
이름이 다르거나 ServiceLoader가 0개/2개이면 fallback으로 숨기지 않고
dependency graph와 descriptor를 다시 조사한다.

이 단계의 valid gate GREEN과 `git diff --check`를 읽은 뒤, execution-gate
변경만 두 번째 Lore commit(`0 tests 실행 수 gate를 fail-closed로 고정`)으로
생성한다. gate commit은 첫 번째 provider/test commit에 의존하지만, 첫 commit을
selective revert해도 보고서 부재나 `0 tests`를 실패로 판정해야 한다.

## Task 4: JDK25 KDoc와 README locale 계약 갱신

**Files:**
- Modify: `Ex01_VirtualThreads.kt` KDoc와 MariaDB assumption line
- Modify: `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md`
- Modify: `08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Do not modify: `docs/images/readme-diagrams/**`

- [ ] **Step 1: source KDoc와 capability assumption을 갱신한다.**

KDoc의 `JDK 21` 설명을 `JDK 25`로 바꾸고, 이 예제가 JDK25 provider와
`runSuspendVT`/`virtualThreadTransaction`/`inTopLevelSuspendTransaction`을
조합한다는 현재 계약을 유지한다. 중첩 transaction test의 assumption은 다음
처럼 stable reason을 남긴다.

```kotlin
Assumptions.assumeTrue(
    testDB !in TestDB.ALL_MARIADB_LIKE,
    "MariaDB-compatible nested transactions are not supported",
)
```

기존 네 parameterized test의 SQL, transaction helper, retry 값, table 정의와
`TestDB` lifecycle은 변경하지 않는다.

- [ ] **Step 2: module English README를 JDK25 실행 계약으로 갱신한다.**

다음 사실을 같은 문서 안에서 일관되게 갱신한다.

```markdown
Learn how to perform asynchronous database operations in an Exposed R2DBC + Java 25 Virtual Threads environment.

> **Requirement**: JDK 25 (`@EnabledOnJre(JRE.JAVA_25)` condition applied)
```

Learning objectives, code snippets, benefits heading/table, cautions, and
reference link의 JDK 버전을 JDK25로 맞춘다. `JDK requirement`는 이 모듈의
exact annotation과 맞도록 `25` 또는 `JDK 25`로 쓴다. provider expectation을
다음과 같이 추가한다: Version Catalog의 versionless alias가
`bluetape4k-dependencies:1.4.0` BOM으로
`bluetape4k-virtualthread-jdk25:1.12.1`을 resolve하고,
`jdk25-structured-task-scope`/`jdk25` public names가 선택되어야 한다.

실행 절에는 다음 명령과 기대값을 포함한다.

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution -PuseFastDB=true
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution
```

fast 실행은 `5 executed / 0 skipped`, 기본 `H2,POSTGRESQL,MYSQL_V8`는
`13 executed / 0 skipped`를 요구한다. `MARIADB` 또는 `H2_MARIADB`를 명시한
실행에서는 중첩 transaction testcase의 capability skip만 허용한다. 테스트
JVM에 production secret을 주입하지 않고 raw environment/system-property
dump를 남기지 않는다는 운영 경계를 문서에 포함한다.

- [ ] **Step 3: module Korean README를 독립적으로 자연스럽게 작성하고 사실 parity를 확인한다.**

한국어 문서는 `JDK 25`, `JRE.JAVA_25`, `jdk25-structured-task-scope`,
`bluetape4k-virtualthread-jdk25:1.12.1`, 명령, 실행 수, MariaDB capability
skip을 영어 문서와 같은 의미·수치로 유지한다. 문장 골격을 기계적으로
번역하지 않고 `실행`, `검증`, `허용`, `차단` 같은 구체 동사를 사용한다.

- [ ] **Step 4: root README 두 locale에 직접 학습 링크와 전제를 추가한다.**

기존 Coroutines 링크를 유지하면서 learning path와 module map에 다음 모듈을
직접 가리키는 항목을 추가한다.

```markdown
08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md
```

루트 문서에는 JDK25 toolchain과 Virtual Threads 예제가 JDK25 provider를
사용한다는 사실을 짧게 명시한다. 영어/한국어 문서에서 링크 대상, provider
version, JDK prerequisite, 실행 command가 대응해야 하며 기존 PNG asset과
alt text/geometry는 바꾸지 않는다.

- [ ] **Step 5: 문서·source stale token과 link를 검사한다.**

```bash
rg -n 'JDK 21|Java 21|JAVA_21|JRE\.JAVA_21|virtualthread-jdk21' \
  README.md README.ko.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.ko.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/src/test/kotlin/exposed/r2dbc/examples/virtualthreads/Ex01_VirtualThreads.kt
rg -n 'JDK 25|Java 25|JAVA_25|JRE\.JAVA_25|virtualthread-jdk25|jdk25-structured-task-scope' \
  README.md README.ko.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/README.ko.md \
  08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/src/test/kotlin/exposed/r2dbc/examples/virtualthreads/Ex01_VirtualThreads.kt
```

첫 명령은 의도한 JDK21 exclusion을 포함하지 않는 reader/source 범위에서
결과가 없어야 한다. 두 README의 링크는 `test -e`로 대상 파일을 확인한다.
기존 design/review 문서에 남은 JDK21 baseline evidence는 historical source라서
이 reader/source audit의 대상이 아니다.

## Task 5: full matrix, execution gate와 repository hazard 검증

**Files:**
- Read: module test XML, Gradle reports, dependency reports, Kover output
- Modify: `docs/superpowers/checklists/2026-08-28-issue-198-type-a.md` with fresh evidence
- Do not modify: production code, module registration, diagrams

모든 XML/log 검증은 repository root에서 실행하되 결과가 생성되는 모듈 경로를
명시적으로 고정한다. 각 음성 fixture 전에는 `REPORT_DIR`와 입력 report의
존재를 확인하고, fixture 종료 뒤에는 원본 backup을 `cp`로 복원한 다음
`cmp -s`로 byte-identical 여부를 확인한다.

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
test -d "$REPORT_DIR"
```

- [ ] **Step 1: explicit MariaDB capability allowlist를 검증한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2_MARIADB --no-daemon --console=plain
```

Expected evidence: provider smoke 1개와 non-MariaDB nested transaction을 포함한
parameterized 3개가 현재 관찰값 `total=5 executed=4 skipped=1`로 실행되며,
skip은 정확히 `중첩된 virtual thread 용 트랜잭션을 async로 실행 H2_MARIADB`
하나이고 type/message는 JUnit의 `TestAbortedException` serialized value와
일치해야 한다. 이 값은 현재 matrix의 evidence이고 gate contract는
`minimumTotal=5`, `minimumExecuted=4`, `skipped=1`이다. 다른 skip, failure,
error, 또는 명시적 MariaDB가 아닌 환경의 skip은 task failure다. 이어서 생성된
JUnit XML의 capability message를 임시 복사본에서 다른 문자열로 바꾸고 `-x
test`로 gate만 재실행했을 때도 task failure가 되어야 한다. 원본 XML은 검증
후 즉시 복구하고 backup과 `cmp -s` 결과를 남긴다.

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
test -d "$REPORT_DIR"
REPORT=""
while IFS= read -r candidate; do
  if rg -q --fixed-strings -- 'MariaDB-compatible nested transactions are not supported' "$candidate"; then
    REPORT="$candidate"
    break
  fi
done < <(find "$REPORT_DIR" -type f -name 'TEST-*.xml' -print)
test -n "$REPORT"
test -f "$REPORT"
python3 - "$REPORT" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
skipped = [case for case in root.findall("testcase") if case.find("skipped") is not None]
assert len(skipped) == 1
case = skipped[0]
assert case.attrib["name"] == "중첩된 virtual thread 용 트랜잭션을 async로 실행 H2_MARIADB"
skipped_node = case.find("skipped")
assert skipped_node.attrib["type"] == "org.opentest4j.TestAbortedException"
assert skipped_node.attrib["message"] == (
    "org.opentest4j.TestAbortedException: Assumption failed: "
    "MariaDB-compatible nested transactions are not supported"
)
PY
BACKUP="${REPORT}.issue-198-backup"
cp "$REPORT" "$BACKUP"
restore_report() {
  cp "$BACKUP" "$REPORT"
  cmp -s "$BACKUP" "$REPORT"
  rm -f "$BACKUP"
}
trap restore_report EXIT
python3 - "$REPORT" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
mutated = text.replace(
    "MariaDB-compatible nested transactions are not supported",
    "unexpected capability skip reason",
    1,
)
assert mutated != text
path.write_text(mutated)
PY
set +e
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2_MARIADB -x test --no-daemon --console=plain
status=$?
set -e
test "$status" -ne 0
cp "$BACKUP" "$REPORT"
cmp -s "$BACKUP" "$REPORT"
rm -f "$BACKUP"
trap - EXIT
```

위 negative command는 의도적으로 실패해야 한다. 명령 실행 구간만 `set +e`로
감싸 status를 보존하고, 원본 report를 복구한 뒤 `cmp -s`가 성공해야 다음
fixture로 진행한다. 실패·중단 시에도 별도 `trap`으로 backup을 복구하고
`cmp -s`를 수행한다.

같은 임시 복사본 절차로 testcase 이름에 suffix를 붙인 crafted XML도 gate가
거부하는지 확인한다. 이름 비교는 `contains`가 아니라 exact equality여야 한다.

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
test -d "$REPORT_DIR"
REPORT=""
while IFS= read -r candidate; do
  if rg -q --fixed-strings -- '중첩된 virtual thread 용 트랜잭션을 async로 실행 H2_MARIADB' "$candidate"; then
    REPORT="$candidate"
    break
  fi
done < <(find "$REPORT_DIR" -type f -name 'TEST-*.xml' -print)
test -n "$REPORT"
test -f "$REPORT"
BACKUP="${REPORT}.issue-198-name-backup"
cp "$REPORT" "$BACKUP"
restore_report() {
  cp "$BACKUP" "$REPORT"
  cmp -s "$BACKUP" "$REPORT"
  rm -f "$BACKUP"
}
trap restore_report EXIT
python3 - "$REPORT" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
mutated = text.replace(
    "중첩된 virtual thread 용 트랜잭션을 async로 실행 H2_MARIADB",
    "중첩된 virtual thread 용 트랜잭션을 async로 실행 H2_MARIADB crafted",
    1,
)
assert mutated != text
path.write_text(mutated)
PY
set +e
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2_MARIADB -x test --no-daemon --console=plain
status=$?
set -e
test "$status" -ne 0
cp "$BACKUP" "$REPORT"
cmp -s "$BACKUP" "$REPORT"
rm -f "$BACKUP"
trap - EXIT
```

이름 crafted negative도 의도적으로 실패해야 하며, 원본 report 복구 후 다음
검증을 진행한다. `REPORT_DIR` 밖의 root-level `build/test-results`는 읽지 않는다.

root tag와 선언된 testcase 수도 각각 음성 fixture로 검증한다. 두 fixture 모두
앞의 report 선택·backup/restore·`cmp -s` 절차를 재사용하고, 실제 XML 구조는
유지한 채 root 이름만 `testsuite`에서 `suite`로 바꾸거나 첫 `tests` 속성 값을
1만큼 줄인다. 각 gate 실행은 `set +e` 구간에서 status를 저장해 non-zero인지
확인하고, `test -n "$REPORT"`, `test -f "$REPORT"`, 복원 후 `cmp -s`를
통과해야 한다. root fixture는 `JUnit XML root가 testsuite가 아닙니다`, count
fixture는 `JUnit XML testcase 수가 선언값과 다릅니다` 메시지를 확인한다.

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
REPORT="$(find "$REPORT_DIR" -type f -name 'TEST-*.xml' -print -quit)"
test -n "$REPORT"
test -f "$REPORT"
BACKUP="${REPORT}.issue-198-root-backup"
OUTPUT="$(mktemp)"
cp "$REPORT" "$BACKUP"
restore_report() {
  cp "$BACKUP" "$REPORT"
  cmp -s "$BACKUP" "$REPORT"
  rm -f "$BACKUP" "$OUTPUT"
}
trap restore_report EXIT
python3 - "$REPORT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
mutated = text.replace("<testsuite ", "<suite ", 1).replace("</testsuite>", "</suite>", 1)
assert mutated != text
path.write_text(mutated)
PY
set +e
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2_MARIADB -x test --no-daemon --console=plain >"$OUTPUT" 2>&1
status=$?
set -e
test "$status" -ne 0
rg -q --fixed-strings -- 'JUnit XML root가 testsuite가 아닙니다' "$OUTPUT"
restore_report
trap - EXIT
```

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
REPORT="$(find "$REPORT_DIR" -type f -name 'TEST-*.xml' -print -quit)"
test -n "$REPORT"
test -f "$REPORT"
BACKUP="${REPORT}.issue-198-count-backup"
OUTPUT="$(mktemp)"
cp "$REPORT" "$BACKUP"
restore_report() {
  cp "$BACKUP" "$REPORT"
  cmp -s "$BACKUP" "$REPORT"
  rm -f "$BACKUP" "$OUTPUT"
}
trap restore_report EXIT
python3 - "$REPORT" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()
def decrement(match):
    return f'tests="{int(match.group(1)) - 1}"'
mutated = re.sub(r'tests="(\d+)"', decrement, text, count=1)
assert mutated != text
path.write_text(mutated)
PY
set +e
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2_MARIADB -x test --no-daemon --console=plain >"$OUTPUT" 2>&1
status=$?
set -e
test "$status" -ne 0
rg -q --fixed-strings -- 'JUnit XML testcase 수가 선언값과 다릅니다' "$OUTPUT"
restore_report
trap - EXIT
```

- [ ] **Step 2: default dialect matrix를 직렬로 실행한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
  --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  --no-daemon --console=plain
```

Expected evidence: `H2,POSTGRESQL,MYSQL_V8` 각각의 네 parameterized test와
provider smoke가 `total=13 executed=13 skipped=0 failures=0 errors=0`으로
끝난다. Testcontainers/Colima 오류가 나면 skip으로 인정하지 않고 raw
container/Gradle failure를 진단한 뒤 해당 명령부터 다시 실행한다.

- [ ] **Step 2a: execution-count 입력을 fail-closed로 검증한다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=TYPO -x test --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB=H2, -x test --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseDB= -x test --no-daemon --console=plain
```

각 명령은 `unknown/empty dialect token` 메시지로 실패해야 하며 H2 fallback이나
`0 tests` green을 남기지 않는다. 실패 output에는 property 값·환경 변수·credential
값을 그대로 출력하지 않는다. 이 세 음성 검증은 execution-gate commit에 포함할
수정의 완료 조건이다.

`useFastDB`가 지정된 경우에도 `true`/`false` 이외 값은 기본 matrix로 조용히
전환하지 않아야 한다.

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseFastDB=maybe -x test --no-daemon --console=plain
```

위 명령도 `useFastDB는 true 또는 false여야` 메시지로 실패해야 한다.

- [ ] **Step 2b: smoke liveness를 세 번 반복 측정한다.**

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
LIVENESS_LOG_DIR="$MODULE_DIR/build/reports/issue-198-liveness"
mkdir -p "$LIVENESS_LOG_DIR"
for run in 1 2 3; do
  LOG="$LIVENESS_LOG_DIR/run-${run}.log"
  /usr/bin/time -p ./gradlew :02-exposed-r2dbc-virtualthreads-basic:test \
    -PuseFastDB=true --tests \
    'exposed.r2dbc.examples.virtualthreads.Ex01_VirtualThreads.JDK25 provider와 runtime을 선택하고 structured scope를 닫는다' \
    --no-daemon --console=plain >"$LOG" 2>&1
done
for run in 1 2 3; do
  LOG="$LIVENESS_LOG_DIR/run-${run}.log"
  test -s "$LOG"
  rg -q 'BUILD SUCCESSFUL' "$LOG"
  rg -q '1 test completed' "$LOG"
done
awk '/^real / { print $2 }' "$LIVENESS_LOG_DIR"/run-*.log
```

`set -euo pipefail`과 run별 raw log 보존으로 어느 한 실행의 non-zero를 숨기지
않는다. 세 실행 모두 smoke 한 개만 선택되어 test hang 없이 PASS하고, 내부
deadline/외부 `@Timeout`이 지켜지는 실제 `real` 시간을 기록한다. `BUILD SUCCESSFUL`과
`1 test completed` 확인이 모두 필요하며, fast matrix의 `5/0` 계약은 Step 4와
execution gate에서 별도로 증명한다. 한 실행이라도 실패하면 loop가 즉시 중단된다. min/max/median은 이 test JVM의 liveness 증적일
뿐이며 production latency benchmark나 SLO로 해석하지 않는다. 500ms deadline은
bounded join 계약을 위한 값이고, 유지 근거는 deterministic latch와 세 번의
반복 실행 결과다. 한 번이라도 timeout 또는 child lifecycle flag failure가
발생하면 run별 raw output을 보존하고 timeout을 늘리지 않은 채 stability lane을
재검토한다. `real` 값의 min/max/median을 checklist에 기록하되 성능 기준으로
승격하지 않는다.

- [ ] **Step 3: module registration, compile, static analysis와 coverage를 확인한다.**

```bash
./gradlew projects --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:compileTestKotlin \
  --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:detekt \
  --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:koverXmlReport \
  --no-daemon --console=plain
```

Expected evidence: `projects`에 새 module이 없고 기존 module path가 그대로
보인다. compile/detekt/Kover가 통과하며 Kover task가 없을 때는 먼저
`./gradlew :02-exposed-r2dbc-virtualthreads-basic:tasks --all` 결과를 읽고
실제 동일 coverage task를 한 번 실행한다. task 부재 자체를 테스트 통과로
간주하지 않는다.

- [ ] **Step 4: dependency graph와 ABI/preview 경계를 읽는다.**

```bash
./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencies \
  --configuration testRuntimeClasspath --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencyInsight \
  --dependency bluetape4k-virtualthread-jdk25 \
  --configuration testRuntimeClasspath --no-daemon --console=plain
./gradlew :02-exposed-r2dbc-virtualthreads-basic:dependencyInsight \
  --dependency bluetape4k-virtualthread-jdk21 \
  --configuration testRuntimeClasspath --no-daemon --console=plain
JDK25_JAR="/Users/debop/.gradle/caches/modules-2/files-2.1/io.github.bluetape4k/bluetape4k-virtualthread-jdk25/1.12.1/6ed90ada6fa00481ec92222c9cb573cf0028cf6a/bluetape4k-virtualthread-jdk25-1.12.1.jar"
sha256sum "$JDK25_JAR"
jar tf "$JDK25_JAR" | rg 'META-INF/services/io\.bluetape4k\.concurrent\.virtualthread'
javap -verbose -classpath "$JDK25_JAR" \
  io.bluetape4k.concurrent.virtualthread.jdk25.Jdk25VirtualThreadRuntime \
  | rg 'major version|minor version'
rg -n -- '--enable-preview' build.gradle.kts 08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic/build.gradle.kts
```

Expected evidence: resolved JDK25 provider는 정확히 하나, JDK21 provider
dependencyInsight는 resolved result를 내지 않는다. provider classfile은
`major=69`, `minor=0`, Gradle module metadata는 `org.gradle.jvm.version=25`,
ServiceLoader descriptor 두 개의 payload와 JAR SHA-256이 정확히 일치한다.
각 비교 명령은 mismatch에서 non-zero로 끝나며 `|| true`로 실패를 숨기지
않는다. `--enable-preview`는 새로 추가되지 않는다. classfile/metadata가
다르면 artifact identity를 먼저 repair하고 테스트 결과를 재사용하지 않는다. 현재 저장소에
`gradle/verification-metadata.xml`이 없다는 사실도 확인하고 이번 계획에서
새 dependency verification 정책을 만들지 않는다.

```bash
JDK25_ROOT="/Users/debop/.gradle/caches/modules-2/files-2.1/io.github.bluetape4k/bluetape4k-virtualthread-jdk25/1.12.1"
JDK25_JAR="$JDK25_ROOT/6ed90ada6fa00481ec92222c9cb573cf0028cf6a/bluetape4k-virtualthread-jdk25-1.12.1.jar"
JDK25_MODULE="$(find "$JDK25_ROOT" -type f -name '*.module' -print -quit)"
test -f "$JDK25_JAR"
test -f "$JDK25_MODULE"
test "$(sha256sum "$JDK25_JAR" | awk '{print $1}')" = \
  "aab053515aba60ce238dc49d2bccc4c2d05f640f3ce8bf4746c680b616a964a9"
python3 - "$JDK25_MODULE" <<'PY'
import json
import sys
from pathlib import Path

metadata = json.loads(Path(sys.argv[1]).read_text())
variants = metadata["variants"]
assert variants and all(
    variant["attributes"].get("org.gradle.jvm.version") == 25
    for variant in variants
)
assert all(
    file_entry["sha256"] == "aab053515aba60ce238dc49d2bccc4c2d05f640f3ce8bf4746c680b616a964a9"
    for variant in variants
    for file_entry in variant.get("files", [])
)
PY
SCOPE_SERVICE='META-INF/services/io.bluetape4k.concurrent.virtualthread.StructuredTaskScopeProvider'
RUNTIME_SERVICE='META-INF/services/io.bluetape4k.concurrent.virtualthread.VirtualThreadRuntime'
for descriptor in "$SCOPE_SERVICE" "$RUNTIME_SERVICE"; do
  test "$(jar tf "$JDK25_JAR" | rg -F -c -x -- "$descriptor")" -eq 1
done
printf '%s\n' 'io.bluetape4k.concurrent.virtualthread.jdk25.Jdk25StructuredTaskScopeProvider' \
  | cmp -s - <(unzip -p "$JDK25_JAR" "$SCOPE_SERVICE")
printf '%s\n' 'io.bluetape4k.concurrent.virtualthread.jdk25.Jdk25VirtualThreadRuntime' \
  | cmp -s - <(unzip -p "$JDK25_JAR" "$RUNTIME_SERVICE")
test ! -e gradle/verification-metadata.xml
```

- [ ] **Step 5: XML gate와 log의 보안 경계를 확인한다.**

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
set +e
rg -n 'System\.getProperties|System\.getenv|print.*environment|dump.*property|EXPOSED_.*PASS|PASSWORD|SECRET|TOKEN' \
  "$REPORT_DIR" "$MODULE_DIR/build/reports/tests" 2>/dev/null
status=$?
set -e
test "$status" -eq 1
```

Expected evidence: test report/log에 raw environment, system property, production
secret, credential 값 dump가 없고 gate log에는 report 수와 집계 수만 남는다.
검색 결과가 있으면 source/log를 확인해 값을 제거하고 관련 보안 review를
재실행한다.

- [ ] **Step 5a: hostile XML fixture가 외부 entity/XInclude를 차단하는지 확인한다.**

```bash
set -euo pipefail
MODULE_DIR="08-r2dbc-coroutines/02-exposed-r2dbc-virtualthreads-basic"
REPORT_DIR="$MODULE_DIR/build/test-results/test"
test -d "$REPORT_DIR"
SENTINEL="$(mktemp)"
printf '%s\n' 'ISSUE-198-XXE-SENTINEL' > "$SENTINEL"
REPORT="$(find "$REPORT_DIR" -type f -name 'TEST-*.xml' -print -quit)"
test -n "$REPORT"
test -f "$REPORT"
BACKUP="${REPORT}.issue-198-xml-backup"
OUTPUT="$(mktemp)"
cp "$REPORT" "$BACKUP"
restore_report() {
  cp "$BACKUP" "$REPORT"
  cmp -s "$BACKUP" "$REPORT"
  rm -f "$BACKUP" "$SENTINEL" "$OUTPUT"
}
trap restore_report EXIT
cat > "$REPORT" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "file://$SENTINEL">]>
<testsuite name="fixture" tests="5" skipped="0" failures="1" errors="0"
    xmlns:xi="http://www.w3.org/2001/XInclude">
  <testcase name="fixture" classname="fixture">
    <failure>&xxe;</failure>
    <xi:include href="file://$SENTINEL" />
  </testcase>
</testsuite>
XML
set +e
if ./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
  -PuseFastDB=true -x test --no-daemon --console=plain > "$OUTPUT" 2>&1; then
  echo 'hostile XML fixture was unexpectedly accepted' >&2
  status=0
else
  status=$?
fi
set -e
test "$status" -ne 0
if rg -q 'ISSUE-198-XXE-SENTINEL' "$OUTPUT"; then
  echo 'external entity or XInclude content was exposed' >&2
  exit 1
fi
cp "$BACKUP" "$REPORT"
cmp -s "$BACKUP" "$REPORT"
rm -f "$BACKUP" "$SENTINEL" "$OUTPUT"
trap - EXIT
```

DOCTYPE가 거부되고, entity/XInclude가 확장되지 않으며, sentinel 내용이 task
output에 나타나지 않아야 한다. fixture 실행은 의도적으로 실패하고, status를
보존한 뒤 원본 XML을 복구하여 `cmp -s`까지 통과해야 한다. 중단 시에도 같은
복구와 비교를 수행하도록 실행 shell에 backup 복구 trap을 추가한다. 이 검증을
통과시키기 위해 secure parser 설정을 완화하지 않는다.

- [ ] **Step 6: diff와 변경 surface를 닫는다.**

```bash
git diff --check
git diff --name-only origin/develop...HEAD
git status --short
```

Expected evidence: 변경 파일이 catalog, module Gradle/test, root/module README,
lesson/review/checklist/plan 범위 안에 있고 production transaction code,
settings, diagram asset, `CHANGELOG.md`, unrelated worktree가 없다.

```bash
git diff --name-only origin/develop...HEAD | rg '(^|/)(CHANGELOG\.md|docs/images/|settings\.gradle\.kts)$' || true
```

위 명령은 결과가 없어야 하며, diagram/asset 변경 없음은 이번 task의 구체적인
N/A 근거다.

## Task 6: durable lesson과 writer gate

**Files:**
- Create: `docs/lessons/2026-08-28-issue-198-virtualthread-jdk25.md`
- Modify: `docs/review/issue-198-plan-review.md` only after plan review integration
- Modify: `docs/superpowers/checklists/2026-08-28-issue-198-type-a.md`

- [ ] **Step 1: 구현 결과에 근거한 lesson을 작성한다.**

lesson은 다음 구조를 사용하고, 실제 command/결과를 채운다.

```markdown
# Issue #198 JDK25 Virtual Thread provider 정합화 lesson

## Context

## Decision

## Outcome

## Verification evidence

## Miss or surprise

## Future guard
```

`Context`에는 JDK25 baseline이 `0 tests executed`였다는 사실,
`Decision`에는 BOM/versionless alias와 전체 runtime exclusion을 선택한 이유,
`Outcome`/`Verification evidence`에는 provider name, execution counts,
dependency graph와 test commands를 기록한다. `Miss or surprise`에는 실제 RED,
Gradle XML 또는 provider lifecycle에서 관찰된 차이만 쓴다. 재발 방지 guard에는
execution gate, ServiceLoader singleton assertion, JDK25 README prerequisite를
명시한다. 관찰된 lesson이 없으면 Type A checklist의 모든 lesson 후보 범주를
검토한 구체적 N/A 근거를 남기되 빈 문서를 만들지 않는다.

- [ ] **Step 2: Korean technical writer gate를 실행한다.**

계획·review·lesson 각각에 대해 `bluetape-writer`의 SPW-01~05를 적용한다.
source ledger에는 design, module Gradle/test, catalog, root/module README,
JDK25 artifact evidence, issue URL을 적는다. Korean naturalness checklist
`KO-01`~`KO-07`을 읽고, identifier/command/URL/number를 보존하며,
`~를 통해` 남용·번역투·근거 없는 성능 주장을 제거한다. README 두 locale의
링크·명령·수치는 source와 다시 대조한다.

## Task 7: plan review·승인 후 구현 handoff

- [ ] **Step 1: 계획 self-review를 완료한다.**

다음 명령으로 spec coverage, red-flag 문구, type consistency를 확인한다.

```bash
for token in "T""BD" "TO""DO" "add"" appropriate" "적절한"" 처리" "나중에"" 구현"; do
  rg -n "$token" \
  docs/superpowers/plans/2026-08-28-issue-198-virtualthread-jdk25-plan.md
done
rg -n 'AC-0[1-8]|SPW-0[1-5]|rollback|rerun|Kover|MariaDB|ServiceLoader|preview' \
  docs/superpowers/plans/2026-08-28-issue-198-virtualthread-jdk25-plan.md
git diff --check
```

첫 명령은 결과가 없어야 한다. 두 번째 명령은 AC-01~AC-08, writer gate,
rollback/rerun, Kover, MariaDB, ServiceLoader, preview 경계가 계획에 존재함을
보여준다. 이후 `sed -n '1,260p'`와 `sed -n '261,620p'`로 rendered Markdown을
읽고 code fence/header/table/list가 끊기지 않았는지 확인한다.

- [ ] **Step 2: 6관점 plan review를 수행한다.**

`references/review-perspectives.md`의 `artifact_kind=plan`과
`references/step-3r-plan-review.md`를 적용한다. performance, stability,
security, operator/Ops, developer/API, user/caller lane은 이 plan의 정확한
범위만 읽고 P0/P1/P2/P3와 concrete edit를 반환한다. session slot보다 lane이
많으면 wave로 실행하고, 각 lane의 startup ack/lane complete/empty changed
paths/result JSON을 workflow receipt에 기록한다. review artifact에는 각 lane의
근거, N/A 사유, rerun 여부를 보존한다.

- [ ] **Step 3: main integration과 사용자 계획 승인을 완료한다.**

통합 review는 중복 finding을 합치고, spec AC와 plan task의 1:1 traceability,
task ordering, exact commands, docs/KDoc/locale parity, module/Kover hazard,
rollback/rerun, secret/log boundary를 다시 확인한다. P0/P1이 하나라도 있으면
plan을 수정하고 영향 lane과 통합 review를 재실행한다. 최신 integrated table이
`P0=0, P1=0`이고 SPW-01~05가 PASS인 경우에만 사용자에게 계획을
read-back하고 별도 `승인`을 받는다. 그 승인 전에는 Task 2 이후의 code mutation을
시작하지 않는다.

계획 review가 `P0=0, P1=0`으로 수렴하면 기존 workflow run에 계획 artifact
검사를 기록한다. 먼저 기존 spec check의 JSON schema와 run manifest를
읽고, 같은 schema로 `.bluetape/inputs/issue-198-plan-check.json`과
`.bluetape/inputs/issue-198-plan-check-evidence.json`을 만든 뒤 다음 helper를
실행한다. `--expected-head`에는 git commit SHA가 아니라 직전 `verify` 결과의
현재 receipt checksum(소문자 SHA-256)을 사용하고, 각 mutation 전에 새 값을
읽는다.

```bash
FLOW=/Users/debop/.codex/skills/bluetape-workflow/scripts/bluetape-flow.py
receipt_head() {
  python3 "$FLOW" verify --run-id 20260827T185619Z-32b9ae70 \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["checksum"])'
}
python3 "$FLOW" verify --run-id 20260827T185619Z-32b9ae70
sed -n '1,120p' .bluetape/inputs/issue-198-spec-review-check.json
python3 "$FLOW" check-result \
  --run-id 20260827T185619Z-32b9ae70 \
  --owner-file .bluetape/handles/issue-198-owner \
  --expected-head "$(receipt_head)" \
  --input .bluetape/inputs/issue-198-plan-check.json \
  --evidence .bluetape/inputs/issue-198-plan-check-evidence.json
python3 "$FLOW" component-evidence \
  --run-id 20260827T185619Z-32b9ae70 \
  --owner-file .bluetape/handles/issue-198-owner \
  --expected-head "$(receipt_head)" \
  --input .bluetape/inputs/issue-198-plan-check.json \
  --evidence .bluetape/inputs/issue-198-plan-check-evidence.json
python3 "$FLOW" completion-check --run-id 20260827T185619Z-32b9ae70
```

plan check의 evidence에는 plan path, integrated review path, SPW-01~05 결과,
최신 P0/P1 count만 기록하고 secret/raw property를 넣지 않는다. helper 결과가
실패하면 receipt schema와 expected head를 repair한 뒤 같은 check를 재실행한다.

## Acceptance traceability

| Spec AC | 구현 task | 증명 명령/산출물 | 실패 시 되돌림 |
|---|---|---|---|
| AC-01 catalog/BOM provider | Task 3-1/2 | `libs.versions.toml`, `dependencies`, `dependencyInsight` | alias/BOM resolution을 repair하고 Task 3-4 재실행 |
| AC-02 JDK21 0개·JDK25 1개 | Task 3-2, Task 5-4 | `testRuntimeClasspath` graph, ServiceLoader list, exact descriptor payload/count, JAR SHA-256, JDK25 module metadata | exclusion 범위를 configuration 전체로 복구 |
| AC-03 JDK25 실제 실행·0 tests 차단 | Task 2-1, Task 3-3/4, Task 5-1/2a | fast `5/0`, default `13/0`, gate XML count, `TYPO`/blank `useDB`, malformed `useFastDB` negative paths | annotation/gate를 repair하고 RED부터 재실행 |
| AC-04 public provider/runtime discovery | Task 2-2/3 | provider smoke, `providerName`, `runtimeName`, `isSupported`, singleton enumeration | internal class assertion을 추가하지 않고 public contract로 repair |
| AC-05 Exposed R2DBC와 기존 capability 유지 | Task 4-1, Task 5-1/2 | existing four parameterized tests, MariaDB `4/1` allowlist | transaction/table 변경을 revert하고 test lifecycle 재검증 |
| AC-06 bounded structured scope lifecycle | Task 2-2/3, Task 5-2b | deterministic latch, exact child failure type/message, 500ms deadline, external `@Timeout(2s)`, `childStopped=true`, three sequential liveness runs | close/interruption order를 조사하고 timeout을 무작정 늘리지 않음 |
| AC-07 EN/KO root/module README와 KDoc | Task 4 | stale-token/link audit, locale read-back | affected docs를 함께 수정하고 writer gate 재실행 |
| AC-08 module/Kover/diff/Kotlin checks | Task 5-3/6 | `projects`, compile, detekt, Kover, `git diff --check` | module registration/coverage path를 원상 경계에서 repair |

## Triggered risk prediction (A-05)

| 위험 | 신호 | 완화 | rollback/rerun |
|---|---|---|---|
| BOM 또는 versionless alias drift | provider resolution error 또는 1.12.1 이외 resolution | alias에는 version을 중복하지 않고 BOM/metadata를 read-back | catalog/dependency만 revert 후 Task 1-2와 Task 3 재실행 |
| JDK21 provider가 transitive로 남음 | dependency graph에 JDK21 또는 ServiceLoader 2개 | `testRuntimeClasspath.exclude`를 전체 configuration에 적용하고 insight 확인 | module Gradle 수정 후 Task 3-4/5-4 재실행 |
| JDK ABI/preview 불일치 | `UnsupportedClassVersionError`, `NoSuchMethodError`, `--enable-preview` 요구 | classfile `69/0`, module metadata JDK25, provider SHA/service descriptor 확인 | provider artifact 교체 없이 실행을 진행하지 않고 JDK25 forward-fix |
| ServiceLoader descriptor 누락/unknown/duplicate | provider list 0개/2개, name/support mismatch, descriptor count/payload drift | public singleton test와 resolved artifact의 두 descriptor exact count/payload, JAR SHA-256을 함께 검사 | runtime dependency를 단일 provider로 복구 후 Task 2 RED/GREEN 재실행 |
| execution gate false-green | `0 tests`, arbitrary skip, XML 없음, `useDB` 오타/빈 token | XML report 필수, exact total/executed/skipped, fail-closed dialect token validation, testcase name/message allowlist, failure/error=0 | gate failure 원인부터 진단하고 이전 결과를 폐기 |
| MariaDB capability skip 오판 | 명시적 MariaDB 외 skip, nested 아닌 testcase, crafted name/reason | explicit selection count와 exact testcase-name/message multiset allowlist, crafted XML negative path | TestDB capability 범위는 유지하고 gate만 repair |
| structured scope leak/deadlock | latch 이후에도 child flag false, 500ms deadline 초과 또는 test hang | public `failFast`, deterministic `CountDownLatch`, exact child failure type/message, 내부 deadline와 외부 `@Timeout(2s)`, `finally` flag | implementation을 중단하고 provider lifecycle evidence 재수집 |
| Testcontainers/DB lifecycle flake | container startup/connection timeout, retry exhaustion | inherited Colima socket, heavyweight test 직렬화, raw failure 조사 | 실패 명령부터 순차 재실행; skip으로 치환하지 않음 |
| locale/document drift | JDK21 token, broken link, command/count mismatch | EN/KO facts table와 reader/source audit, writer SPW gate | affected README/KDoc 함께 수정 후 Task 4-5 재실행 |
| secret/log boundary 위반 | test report에 env/property/credential dump | gate는 aggregate/path만 log, test JVM에 production secret 미주입 | offending log/source 제거 후 security lane 재검토 |

## Rollback and rerun contract

- technical rollback은 세 Lore commit 경계를 사용한다. provider/test
  연결 commit을 selective revert하고 execution-gate commit은 유지하는 것이
  기본 경로다. gate commit까지 되돌리는 전체 rollback은 임시 진단 경로다.
- rollback 후 JDK25에서 다시 `0 tests executed`가 되는 상태는 비수용이다.
  provider/test commit을 되돌린 상태에서 gate는 report 부재, provider 부재,
  또는 `0 tests`를 실패시켜야 한다. rollback은 원인 분석용 임시 경로이며,
  최종 복구는 JDK25 provider와 실행 수 gate를 다시 적용하는 forward-fix다.
- selective revert 검증 명령은 다음과 같다.

  ```bash
  ./gradlew :02-exposed-r2dbc-virtualthreads-basic:verifyVirtualThreadTestExecution \
    -PuseFastDB=true --no-daemon --console=plain
  ```

  기존 JDK21 조건/provider만 남긴 rollback 상태에서는 이 명령이 `0 tests`
  또는 expected count mismatch로 실패해야 하며, 그 실패를 무시하고 다음
  task로 진행하지 않는다.
- RED/GREEN 순서를 깨뜨린 경우 RED 증적을 새로 만들고 Task 2부터 순차 재실행한다.
- Gradle/DB failure는 해당 명령의 raw output와 XML을 읽은 후 같은 명령을
  재실행한다. retry PASS만으로 lifecycle failure를 지우지 않는다.
- plan/spec의 기술적 의미·acceptance·명령이 바뀌면 사용자 계획 승인을 다시
  받고 영향 plan review lane을 재실행한다.

## Plan writer DoD and handoff state

- [x] **SPW-01 — source ledger/audience/evidence:** 계획 독자는 구현자와 reviewer이며, 위 파일 지도·issue·artifact·spec/review 경로를 기준으로 한다. source ledger를 self-review에서 대조했다.
- [x] **SPW-02 — plan artifact contract:** task dependency, exact files, TDD, commands, expected evidence, docs, hazards, rollback/rerun, approval gate를 모두 작성했다.
- [x] **SPW-03 — Korean technical register:** `korean-naturalness-checklist.md`의 KO-01~KO-07을 읽고 Korean 계획 문체·용어·technical token 보존을 점검했다.
- [x] **SPW-04 — spec-to-plan traceability:** AC-01~AC-08 traceability 표와 failure rollback을 design contract와 대조했다.
- [x] **SPW-05 — rendered read-back:** red-flag scan, `git diff --check`, heading/table/code-fence read-back을 완료했다. 통합 plan review artifact는 별도 Step 7에서 추가한다.

이 문서는 계획 승인 전 상태다. plan review 통합 결과가 P0/P1 0이고 사용자
계획 `승인`이 기록될 때까지 Type A A-04와 구현 단계는 `PENDING`이다.
