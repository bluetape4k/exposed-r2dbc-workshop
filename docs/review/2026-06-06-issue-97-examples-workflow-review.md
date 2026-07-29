# Issue 97 Examples Workflow 검토

## 범위

- `.github/workflows/Examples.yml`
- `docs/lessons/2026-06-06-issue-97-examples-weekly.md`

## 검토 결과

- P0: 0
- P1: 0
- P2: 0

## 발견 사항

차단 findings 없음.

## 증거

- Workflow에는 weekly schedule이 하나 있다: `30 21 * * 0`.
- `workflow_dispatch`, push path filter, pull request path filter가 계속 존재한다.
- Selected example job은 계속 `-PuseDB=H2`를 사용하므로 이 workflow는 default smoke path에 남아 있다.
- 각 chapter job은 이미 7일 retention이 있는 test result artifact를 upload한다.
- `actionlint .github/workflows/Examples.yml` 통과.
- `git diff --check` 통과.

## 잔여 위험

Merge 전에 GitHub Actions checks가 PR에서 완료되어야 한다. 이 workflow-only change는 새 example module을 추가하지 않는다.
