# Issue #204 workflow 정렬 검토

검토일: 2026-08-27

## 결과

`09-spring/06-exposed-spring-boot-r2dbc-repository`가 Examples workflow의 path filter와 전용 H2 job에 연결되었다. 기존 CI의 changed-task 자동 선택과 Nightly H2 full 실행은 새 leaf module을 이미 포괄하므로 중복 shard 등록은 하지 않았다.

## Examples workflow

- `.github/workflows/Examples.yml`의 `push.paths`와 `pull_request.paths`에 다음 경로를 추가했다.
  `09-spring/06-exposed-spring-boot-r2dbc-repository/**`
- `chapter-09` job은 저장소 공통 Java 25 Temurin setup과 Gradle wrapper를 사용한다.
- 실행 task는 다음 하나로 고정했다.

  ```bash
  ./gradlew :06-exposed-spring-boot-r2dbc-repository:test -PuseDB=H2 --continue
  ```

- `09-spring/06-exposed-spring-boot-r2dbc-repository/build/test-results/test/*.xml`과 `build/reports/tests/test/`를 7일 artifact로 업로드한다.
- 모듈의 H2 `regular` profile만 검증하므로 PostgreSQL/MySQL/MariaDB compatibility gate로 해석하지 않는다.

## CI dynamic mapping

`.github/scripts/changed-r2dbc-test-tasks.py`는 첫 경로 `09-spring` 아래에서 `build.gradle.kts`가 있는 leaf directory를 찾아 task 이름을 생성한다. 신규 모듈 파일을 넣은 read-only 실행 결과는 다음과 같다.

```text
:06-exposed-spring-boot-r2dbc-repository:test
:06-exposed-spring-boot-r2dbc-repository:koverXmlReport
```

따라서 `ci.yml`의 변경 모듈 선택 및 H2 Kover artifact 흐름을 별도 등록 없이 사용한다. `ci.yml`의 `paths-ignore`는 Kotlin/build 파일 변경을 계속 CI에 전달하며, `README`/diagram-only 변경은 기존 정책대로 CI 대신 Examples path에 해당한다.

## Nightly shard

- H2 `module-tasks: test` 경로는 root `./gradlew test`를 실행하므로 신규 leaf를 포함한다.
- PostgreSQL/MySQL/MariaDB 명시 shard에는 신규 task를 추가하지 않았다. 해당 모듈은 H2 `regular` profile만 지원하고 외부 DB 설정을 제공하지 않기 때문이다.
- Nightly의 기존 wildcard artifact `**/build/test-results/test/*.xml` 및 `**/build/reports/kover/`는 테스트 결과와 coverage 경로를 포괄한다.
- Examples job은 HTML test report까지 명시 업로드한다. bounded startup smoke와 Gradle failure log는 로컬 T11 검증 범위이며 CI artifact에는 `ConnectionFactoryOptions` 전체 문자열이나 credential을 남기지 않는다.

## 정적 검증

- `actionlint .github/workflows/Examples.yml`: PASS
- `ruby -e 'require "yaml"; YAML.load_file(".github/workflows/Examples.yml")'`: PASS (`yaml-ok`)
- `./gradlew projects --console=plain`: `:06-exposed-spring-boot-r2dbc-repository` 확인
