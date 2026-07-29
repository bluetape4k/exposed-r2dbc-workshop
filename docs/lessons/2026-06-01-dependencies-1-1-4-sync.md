# Dependencies 1.1.4 동기화 교훈

## 맥락

`bluetape4k-dependencies` 1.2.0 release preparation에서는 BOM release CI가 통과할 수 있도록 downstream workshop이 central shared-version source of truth와 먼저 일치해야 했다.

## 결정

Workshop catalog를 최신 published `bluetape4k-dependencies:1.1.4` baseline과 central shared runtime version에 맞춘다. `1.2.0`은 게시되기 전까지 소비하지 않는다.

## 결과

Workshop은 central release preflight에서 더 이상 shared-version drift를 보고하지 않는다.

## 검증

`bluetape4k-dependencies`에서 `sync-shared-versions.py --workspace /Users/debop/work/bluetape4k --write --check --summary`로 검증했다.
