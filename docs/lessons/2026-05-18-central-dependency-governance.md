# 중앙 의존성 거버넌스 동기화

## 맥락

Downstream Dependabot PR이 저장소별로 shared dependency version을 각각 갱신하면서 bluetape4k organization 안에 version drift가 생기고 있었다.

## 결정

Shared dependency version은 먼저 `bluetape4k-dependencies`에서 바꾸고, 그다음 `sync-shared-versions.py`로 이 저장소에 materialize한다. 이 저장소의 Dependabot도 centrally governed dependency name을 ignore해서 이후 PR이 중앙 source of truth를 통하도록 한다.

## 결과

Local version catalog와 `.github/dependabot.yml`이 중앙 dependency-governance policy를 따른다.

## 검증

- 이 저장소 대상으로 `sync-shared-versions.py --write --check --summary`
- 이 저장소 대상으로 `sync-dependabot-ignores.py --write --check --summary`
- `git diff --check`

## 향후 방어선

Centrally governed dependency에 대한 repo-local Dependabot PR은 병합하지 않는다. 먼저 `bluetape4k-dependencies`를 갱신한 뒤 이 저장소를 sync한다.
