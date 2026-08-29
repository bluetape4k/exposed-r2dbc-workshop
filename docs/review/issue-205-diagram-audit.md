# Issue #205 diagram 감사

검토일: 2026-08-29

## 자산

| 의미 | English | 한국어 |
|---|---|---|
| Architecture | `13-09-checkpointable-r2dbc-batch-architecture-01-en.svg/.png` | `...-architecture-01-ko.svg/.png` |
| Lifecycle | `13-09-checkpointable-r2dbc-batch-lifecycle-01-en.svg/.png` | `...-lifecycle-01-ko.svg/.png` |

각 locale는 동일한 caller → job DSL → reader/processor/writer → metadata와
source/target DB topology를 유지하며, README는 locale에 맞는 PNG만 embed하고
SVG는 source asset으로 보존한다.

## 감사 결과

- architecture semantic ledger: `ok=true`, nodes=10, edges=11, branches=2, loops=1
- lifecycle semantic ledger: `ok=true`, nodes=7, edges=11, branches=2, loops=1
- `xmllint --noout`: 네 SVG PASS
- geometry (`--fail-diagonal`): 네 SVG `geometry_failures=0`
- mixed-corner/endpoint/arrowhead/connector audit: 네 SVG 모두 PASS; crossing/shared endpoint 없음
- text normalization: `text_hazards=0`, `code_without_highlight=0`
- PNG render/visual audit: 네 PNG 모두 opaque와 clipping 없음; architecture aspect 1.636, lifecycle aspect 1.133
- full-size image inspection: 네 PNG의 제목·번호 pill·lifeline·분기·arrowhead가 잘리지 않음
- module README link audit: `missing_refs=[]`, `svg_refs=[]`, `duplicates={}`; 전체 공용 directory pair audit도 `missing_svg=[]`, `missing_png=[]`로 확인
- 새 네 pair만 분리한 asset audit: `svg_count=4`, `png_count=4`, `pair_count=4`, `failures=[]`
- 이번 재검증에서도 lifecycle diagram은 공통 cancellation topology를 시각화하고,
  `FAILED` checkpoint/restart는 module README의 failure matrix와 회귀 테스트로
  별도 증명하는 범위를 유지한다.

shared `docs/images/readme-diagrams` 전체를 `--require-all-referenced`로
검사하는 명령은 기존 공용 자산까지 이 module README가 모두 embed해야 하는
범위를 요구하므로 이 issue의 strict 증거로 사용하지 않는다. 따라서 해당
global strict 결과는 N/A이며, 새 네 topology pair와 실제 README link를
검증한 결과만 DoD에 반영한다.
