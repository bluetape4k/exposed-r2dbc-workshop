# Dependencies 1.1.1 동기화 교훈

## 맥락

Artifact availability audit에서 게시되지 않은 mock web application module에 대한 generated alias가 발견된 뒤 `bluetape4k-dependencies` 1.1.0은 1.1.1로 대체되었다. 이 workshop은 shared catalog를 소비하고, artifact availability가 고쳐진 뒤 repository-specific R2DBC 동작을 드러낼 수 있는 database matrix도 실행한다.

## 결정

표준 shared-version sync path를 통해 `bluetape4k-dependencies = "1.1.1"`을 사용한다. Catalog availability 수정은 R2DBC compatibility 수정과 분리해서 유지한다.

## 결과

PR #85는 이 repository를 1.1.1 catalog에 맞췄고 CI 통과 후 병합됐다. 이후 PostgreSQL matrix가 Exposed R2DBC batch insert generated-key 문제를 드러냈으며, 이는 `docs/lessons/2026-05-23-r2dbc-batch-insert-generated-values.md`에 별도로 기록했다.

## 검증

- `./gradlew :exposed-r2dbc-shared:test -PuseDB=POSTGRESQL --max-workers=1 --continue`
- GitHub PR #85 status checks passed before merge.
- Downstream PR 병합 후 workspace-level `scripts/sync-shared-versions.py --workspace .. --check --summary` 통과.

## 향후 지침

Shared catalog patch가 publication availability를 고치면, downstream CI를 다시 돌리기 전에 Maven Central `repo1`에서 새 version을 resolve할 수 있는지 기다린다. 이후 database matrix가 실패하면 dependency sync는 보존하고 repository-specific database behavior를 같은 PR이나 follow-up patch에서 고친다.
