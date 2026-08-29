# Issue #198 JDK25 Virtual Thread provider 정합화 교훈

## 배경

`02-exposed-r2dbc-virtualthreads-basic`은 JDK25에서 실행되어야 하지만,
기존 테스트 조건은 `JRE.JAVA_21`이었고 test runtime에는 JDK21 provider가
남아 있었다. JDK25 환경에서 baseline은 Gradle `BUILD SUCCESSFUL`이었지만
JUnit XML에는 `tests=4, skipped=4`만 남아 실제 실행 수가 0이었다. JDK21
provider를 그대로 로드하면 JDK25 JVM에서
`UnsupportedClassVersionError`가 발생했다.

## 결정

- 테스트 조건을 `@EnabledOnJre(JRE.JAVA_25)`로 고정하고 public API를 통한
  ServiceLoader smoke test를 추가했다.
- Version Catalog에는 versionless
  `bluetape4k-virtualthread-jdk25` alias만 두고,
  `bluetape4k-dependencies:1.4.0` BOM이 `1.12.1`을 결정하게 했다.
- `testRuntimeClasspath`에서 `bluetape4k-virtualthread-jdk21`을 제외해
  JDK25 provider 하나만 선택되도록 했다.
- `verifyVirtualThreadTestExecution` Gradle task가 test를 먼저 실행하고
  JUnit XML의 report 존재, testcase identity, failure/error, MariaDB
  capability skip tuple, 최소 실행 수를 fail-closed로 확인한다.
- provider smoke는 bounded structured-scope failure와 child interruption을
  함께 확인하며, MariaDB-compatible nested transaction은 명시적 capability
  사유로만 skip한다.

## 결과

1. dependency를 바꾸기 전에 JDK25 조건과 provider smoke를 추가해 RED를
   확보했다. 기존 JDK21 provider가 ServiceLoader에서 로드되면서
   `UnsupportedClassVersionError`가 발생했다.
2. JDK25 provider 연결 후 fast matrix는 `5 executed / 0 skipped`로 GREEN이
   되었고, H2_MARIADB 명시 실행은 `5 total / 4 executed / 1 skipped`였다.
3. `-PuseDB=TYPO`, `-PuseDB=H2,`, `-PuseDB=`,
   `-PuseFastDB=maybe`는 모두 execution gate가 non-zero로 거부했다.
4. Gradle task를 script `doLast`에서 managed task class로 분리하지 않으면
   configuration cache가 script object reference를 직렬화하지 못한다. 현재
   task는 `Property`/`DirectoryProperty` 입력을 사용하고 configuration cache
   저장을 통과한다.

## 검증 증적

- `./gradlew :02-exposed-r2dbc-virtualthreads-basic:test`와
  `verifyVirtualThreadTestExecution` 기본 matrix가 `13 total / 13 executed /
  0 skipped / 0 failures / 0 errors`로 완료됐다.
- `-PuseDB=H2_MARIADB` 실행은 `5 total / 4 executed / 1 skipped`이며,
  skip tuple은 `Ex01_VirtualThreads`의 중첩 트랜잭션 testcase와
  `org.opentest4j.TestAbortedException` capability message 하나로 고정됐다.
- malformed `useDB` 3종과 `useFastDB=maybe`는 각각 non-zero와 fail-closed
  메시지를 남겼다. capability/message, exact testcase identity, wrong classname,
  duplicate/nested skip, root/count 위반, aggregate 속성과 불일치하는
  `<failure>`/`<error>` node hostile XML 12종도 모두 의도한 오류로 거부됐고
  원본 report를 `cmp -s`로 복구했다.
- DOCTYPE fixture는 `DOCTYPE is disallowed`로 거부됐고, XInclude fixture는
  외부 sentinel을 확장하지 않은 채 `5/4/1` gate를 통과했다.
- JDK25 provider artifact는 dependency insight에서 단일 resolved 결과로
  확인했고, JAR SHA-256은
  `aab053515aba60ce238dc49d2bccc4c2d05f640f3ce8bf4746c680b616a964a9`,
  classfile은 `major=69/minor=0`, ServiceLoader descriptor 2개와 Gradle
  metadata `org.gradle.jvm.version=25`로 검증했다. JDK21 provider는 runtime
  graph와 insight에 없다.
- `projects`, `compileTestKotlin`, module `check`, `koverXmlReport`가
  `BUILD SUCCESSFUL`로 완료됐다. module `detekt` task는 `tasks --all`에
  등록되지 않아 capability N/A로 기록했으며, task 부재를 성공으로 오인하지
  않았다.
- provider smoke를 no-cache로 세 번 반복해 `real=9.53s/9.85s/9.71s`와
  각 XML `tests=1`, `skipped/failures/errors=0`을 확인했다.

## 관찰된 차이

JDK25 default matrix의 JUnit XML에는 shared `bluetape4k-testcontainers`의
기존 `writeToSystemProperties()`가 출력한 `testcontainers.*.password=test`가
포함됐다. 이는 이 테스트 인프라의 고정 test credential이며 이번 변경이 새로
주입하거나 gate log에 출력한 production secret은 아니다. gate/liveness/negative
캡처 log에 대해 동일한 case-insensitive scan을 실행했을 때 environment,
system property, password, secret, token 패턴은 0건이었다. report-wide scan의
이 기존 test-only 출력은 별도 shared test-infrastructure hardening 범위로
남긴다.

## 재발 방지 guard

- virtual-thread provider를 변경할 때 implementation class를 테스트에 직접
  import하지 말고 public ServiceLoader name/support contract를 유지한다.
- JDK 조건을 바꾸거나 parameterized display name을 추가하면 execution gate의
  exact identity와 README의 관찰값을 함께 갱신한다.
- `verifyVirtualThreadTestExecution`의 secure XML parser 설정, malformed
  property 선행 검증, MariaDB skip allowlist를 완화하지 않는다.
- 실행 수만으로 성공을 주장하지 말고 dependency graph에서 JDK21 provider
  혼입이 없는지와 resolved JDK25 artifact identity를 함께 확인한다.
- 문서의 raw `test` 명령은 진단용으로, authoritative gate는 test를 포함한
  `verifyVirtualThreadTestExecution`으로 안내한다.

## 2.0.0-SNAPSHOT provider 계약 migration

`bluetape4k-dependencies`를 `2.0.0-SNAPSHOT` train으로 올린 뒤 기존 #198
consumer가 `compileTestKotlin`에서 실패했다. 새 `bluetape4k-virtualthread-api`
artifact는 `StructuredTaskScopeProvider`, `StructuredTaskScopes`,
`VirtualThreadRuntime`, `VirtualThreads`를
`io.bluetape4k.concurrent.virtualthread.api` 패키지로 이동시켰고, 기존 예제는
구 패키지를 import하고 있었다.

구현은 provider implementation이나 별도 compatibility layer를 추가하지 않고
`Ex01_VirtualThreads.kt`의 네 public API import만 새 패키지로 바꿨다. `newVT`
및 Exposed R2DBC transaction helper처럼 core에 남은 API와 ServiceLoader
provider implementation은 변경하지 않았다.

검증 결과는 다음과 같다.

- `2.0.0-SNAPSHOT` `compileTestKotlin`: `BUILD SUCCESSFUL`
- `:02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true`: `tests=5,
  skipped=0, failures=0, errors=0`
- 같은 모듈 `check -PuseFastDB=true`: `BUILD SUCCESSFUL` 및 Kover verify 통과

따라서 #198의 원래 JDK25 provider 정합화 의미는 유지하면서, 현재 snapshot
API namespace와 consumer source를 다시 정렬했다. 이후 provider major train을
올릴 때는 catalog resolution만 보지 말고 public API package와 ServiceLoader
descriptor를 함께 read-back해야 한다.

## Writer gate

- SPW-01: PASS — baseline failure, provider 선택, execution gate 목적을 명시했다.
- SPW-02: PASS — TDD RED/GREEN, malformed input, configuration-cache guard와
  후속 검증 범위를 실행 가능한 명령으로 기록했다.
- SPW-03: PASS — 독자-facing lesson은 한국어로 작성하고 API·경로·명령·오류는
  원문을 보존했다.
- SPW-04: PASS — 구현 결정과 다음 변경자가 유지해야 할 guard를 분리했다.
- SPW-05: PASS — default/H2_MARIADB matrix, hostile XML, artifact ABI/metadata,
  static/Kover, liveness와 reader/source parity를 final read-back했다.
