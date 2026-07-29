# Issue 97 Examples Weekly Gate 교훈

## 맥락

`exposed-r2dbc-workshop`에는 selected multi-tenant, high-performance, production-integration example용 `Examples` workflow가 이미 있었다. Issue #97은 manual dispatch와 path-filtered PR coverage를 유지하면서 이 workflow를 weekly downstream R2DBC scenario gate로 만들 것을 요구했다.

## 결정

- 기존 `Examples` workflow에 weekly schedule 하나를 추가한다.
- Selected example은 PR과 scheduled run에서 H2/default smoke path에 유지한다.
- 더 무거운 external database validation은 이 consumer-facing Examples gate가 아니라 CI/Nightly에 남긴다.

## 결과

Workflow는 CI와 Nightly에서 분리된 상태를 유지하고, `workflow_dispatch`, push/PR path filter, chapter job별 test result artifact upload도 계속 유지한다.

## 검증

- `actionlint .github/workflows/Examples.yml`
- `git diff --check`

## 향후 지침

R2DBC downstream example을 추가할 때는 representative consumer smoke coverage에 weekly Examples workflow를 우선 사용하고, 이 path에는 external credential을 추가하지 않는다.
