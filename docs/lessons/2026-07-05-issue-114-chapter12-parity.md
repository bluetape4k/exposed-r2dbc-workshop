# Issue #114 Chapter 12 Parity Refresh 교훈

## 맥락

Issue #114는 `exposed-workshop`이 topic-specific production-integration example 10개를 추가한 뒤 Chapter 12를 다시 검토했다.

## 결정

`exposed-r2dbc-workshop` Chapter 12는 두 runtime module `01-spring-production-integration`, `02-ktor-production-integration`으로 유지한다. R2DBC teaching target은 topic마다 하나의 Gradle module을 만드는 것이 아니라 Spring/Ktor runtime boundary와 `suspendTransaction` repository slice다.

## 방어선

향후 Chapter 12 R2DBC work는 새 issue가 module boundary 자체가 learning goal임을 증명하지 않는 한 기존 두 module 안의 package slice를 확장해야 한다.

Chapter 12 README diagram은 source-backed 상태로 유지한다. Parity table, route flow, outbox/realtime semantic이 바뀌면 architecture parity map과 caller sequence를 모두 갱신한 뒤 SVG에서 PNG를 다시 render한다.

## 검증

- `./gradlew projects`
- `repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --rerun-tasks --console=plain`
- `~/.local/bin/cairosvg docs/images/readme-diagrams/issue-114-chapter12-parity-architecture-01.svg -o docs/images/readme-diagrams/issue-114-chapter12-parity-architecture-01.png -s 2`
- `~/.local/bin/cairosvg docs/images/readme-diagrams/issue-114-chapter12-caller-sequence-01.svg -o docs/images/readme-diagrams/issue-114-chapter12-caller-sequence-01.png -s 2`
