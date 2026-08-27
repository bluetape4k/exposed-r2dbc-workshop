# Issue #198 설계 검토

## 검토 범위와 기준

- 대상: `docs/superpowers/specs/2026-08-28-issue-198-virtualthread-jdk25-design.md`
- 기준 commit: `78ec7bd151b5fc6d2445416c643b9646752c7357`
- live 기준: GitHub Issue #198, JDK25 provider `1.12.1`, 현재 모듈/루트 README
- 검토 방식: 서로 독립적인 6개 관점의 read-only review wave와 main 통합
- 변경/커밋/외부 side effect: 없음 (본 문서 작성 자체는 review 증적)

## 독립 관점 결과

| 관점 | 결과 | P0 | P1 | P2 | P3 | 통합 조치 |
|---|---|---:|---:|---:|---:|---|
| Performance | PASS | 0 | 0 | 0 | 0 | fast/full test와 dependency/provider 검증을 구현 후 실행 |
| Stability | WATCH | 0 | 0 | 3 | 0 | preview classfile, bounded scope lifecycle, false-green rollback을 계획에 고정 |
| Security | COMMENT | 0 | 0 | 2 | 1 | artifact verification 근거와 ServiceLoader 단일성/비허용 구현 차단을 명시 |
| Operations | REQUEST CHANGES | 0 | 1 | 1 | 0 | executed test count와 비의도 skip을 자동 fail하는 gate 추가; dependency 명령 고정 |
| Developer/API | WATCH | 0 | 0 | 2 | 1 | public discovery API assertion과 artifact graph assertion 분리; 전체 runtime exclusion 명시 |
| User/Caller | COMMENT | 0 | 0 | 3 | 0 | root 학습 경로, expected count/allowlist, EN/KO provider 안내 보강 |

## 통합 판단

현재 설계에는 P0가 없고, 구현을 기술적으로 막는 P1은 운영 gate 보강으로 해소할 수
있다. 다음 변경을 설계와 계획에 반영한 뒤 구현을 시작한다.

1. **실행 gate**: fast 실행은 `-PuseFastDB=true`에서 provider smoke test 1개와
   기존 H2 parameterized test 4개의 실행을 기대한다(총 5개, skip 0). 기본 full
   실행은 `-PuseDB=H2,POSTGRESQL,MYSQL_V8`에서 총 13개, skip 0을 기대한다.
   XML 실행 수가 0이거나 의도하지 않은 skip이 있으면 Gradle 검증 task가 실패하도록
   하고, MariaDB capability skip만 명시적 allowlist로 둔다.
2. **Provider 계약**: 테스트는 `providerName`, `runtimeName`, `isSupported`와
   `ServiceLoader`에 등록된 허용 provider 수만 public API로 검증한다. 내부 구현
   class 이름은 계약으로 고정하지 않고, 정확한 JDK25 artifact identity는 resolved
   dependency graph와 service descriptor 증거로 검증한다.
3. **Classpath isolation**: JDK21 exclusion은 JDK25 dependency 선언에 붙이지
   않고 전체 `testRuntimeClasspath` configuration에 적용한다. graph 결과는
   JDK25 provider 정확히 1개, JDK21 provider 0개여야 한다.
4. **Runtime evidence**: provider JAR의 classfile `major=69`, `minor=0`과 Gradle
   module metadata `org.gradle.jvm.version=25`를 read-back해 preview flag가
   필요하지 않음을 고정한다. 이후 `--enable-preview`를 추가하지 않는다.
5. **Stability smoke**: bounded timeout을 사용하는 public structured-scope
   smoke test로 child failure 전파와 close 이후 child 종료를 확인한다. 기존
   Exposed coroutine/transaction 회귀 테스트는 그대로 유지한다.
6. **문서/rollback**: 루트와 모듈 EN/KO README에 직접 링크, JDK25 prerequisite,
   provider 기대 이름, 누락 시 fail-closed 동작을 추가한다. rollback은 기술적
   revert 경로일 뿐 Issue 완료 상태가 아니며, 실행 gate는 revert하지 않는
   forward-fix 원칙을 명시한다.
7. **Catalog 책임**: repository-local `libs.versions.toml`에는 versionless
   alias만 두고, imported `bluetape4k-dependencies:1.4.0` BOM이 provider
   `1.12.1`을 결정한다. BOM 자체는 수정하지 않는다.

## 검토 품질 증적

- SPW-01 (Source): live Issue, 현재 소스/Gradle, published JAR와 설계 문장을
  대조했다.
- SPW-02 (Proof): 모든 finding에 설계 line 범위와 후속 검증 방법을 연결했다.
- SPW-03 (Wording): 한국어 검토 문서이며 code/API/command/token은 원문을
  보존했다.
- SPW-04 (Structure): 관점별 결과, 통합 판단, 다음 gate가 분리되어 있다.
- SPW-05 (Scope): 모듈/루트 문서와 test-runtime만 포함하고 production/API/schema
  변경은 제외한다.

## 다이어그램 범위

기존 README diagram asset의 내용·geometry를 변경하지 않는다. 따라서
`bluetape-diagram` 시각 QA는 N/A이며, 소스 변경 후 링크 존재와 locale parity만
검증한다.

## Verdict

**조건부 PASS — 설계 보강 후 계획 단계로 진행 가능**

P0/P1을 남기지 않는 조건은 위 1~7번 보강을 설계/계획 문서에 반영하고,
`git diff --check`와 독립 review read-back을 다시 통과하는 것이다. 현재 코드
구현은 아직 시작하지 않았다.
