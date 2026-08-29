# Issue #205 workflow 정렬 검토

검토일: 2026-08-29

## Examples workflow

`.github/workflows/Examples.yml`의 기존 `13-ecosystem-integrations/**`
push/pull-request path filter가 새 leaf를 포괄한다. `chapter-13` job은 다음
여섯 모듈을 같은 H2 R2DBC 경로에서 실행한다.

```bash
./gradlew \
  :03-cockroachdb-retry:test \
  :05-ktor-exposed-integration:test \
  :06-spring-modulith-publications:test \
  :07-ddd-aggregate-repository:test \
  :08-ddd-modulith-boundaries:test \
  :09-checkpointable-r2dbc-batch:test \
  -PuseDB=H2 --continue
```

test XML과 HTML report는 기존 wildcard
`13-ecosystem-integrations/**/build/test-results/test/*.xml` 및
`.../build/reports/tests/test/`로 7일 artifact에 수집된다.

## CI/Nightly mapping

`.github/scripts/changed-r2dbc-test-tasks.py`가 Chapter 13 leaf directory의
`build.gradle.kts`를 자동 검색하므로 변경 파일에서
`:09-checkpointable-r2dbc-batch:test`와 `:09-checkpointable-r2dbc-batch:koverXmlReport`
를 생성한다. Nightly H2 `module-tasks: test`는 root full test 경로로 새 leaf를
포괄한다. H2 regular profile만 제공하는 모듈이므로 PostgreSQL/MySQL/MariaDB
명시 shard에는 추가하지 않았다.

## 정적 검증

- `./gradlew projects --no-daemon --console=plain`: `:09-checkpointable-r2dbc-batch` 확인 — PASS
- `actionlint .github/workflows/Examples.yml`: 오류 없음 — PASS
- `ruby -e 'require "yaml"; YAML.load_file(".github/workflows/Examples.yml")'`: yaml-ok — PASS
- Chapter 13 six-module H2 smoke: 29 tests, `BUILD SUCCESSFUL` — PASS
- `changed-r2dbc-test-tasks.py` source-only mapping: `:09-checkpointable-r2dbc-batch:test`와 `:09-checkpointable-r2dbc-batch:koverXmlReport` — PASS

새 모듈은 기존 path filter와 artifact wildcard에 자연스럽게 연결되며,
별도 workflow 파일이나 외부 DB shard를 추가하지 않는 것이 현재 H2-only
계약과 일치한다.
