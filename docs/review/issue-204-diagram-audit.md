# Issue #204 다이어그램 감사 증거

검토일: 2026-08-27

## source와 semantic ledger

- `diagram-semantic-audit.py`: PASS
- nodes=10, edges=10, branches=1, loops=0, source_paths=6
- complexity budget: nodes≤16, edges≤20, branches≤4, loops≤2
- en/ko SVG의 node ID와 edge topology는 동일하며 locale label만 다르다.

## SVG 및 PNG 감사

두 locale에 대해 다음 결과를 확인했다.

- `diagram-svg-text-normalize.py --write`: `text_hazards=0`, `code_without_highlight=0`, `changed=0`
- `xmllint --noout`: PASS
- `diagram-connector-audit.py`: connectors=10, crossings=0, shared_segments=0, PASS
- `diagram-arrowhead-audit.py`: markers=3, used_markers=3, terminal_checks=10, PASS
- `diagram-endpoint-audit.py`: PASS
- `diagram-geometry-audit.py`: geometry_failures=0
- `diagram-mixed-corner-audit.py`: paths=20, q_bends=22, failures=0
- `diagram-visual-audit.py --json`: 3600×2000, aspect=1.8, opaque background, margin imbalance=0, failures=[]
- `view_image(detail=original)`: en/ko 모두 label clipping, arrow 방향, branch crossing, 과도한 여백 없음

## Asset pair와 README 노출

공용 `docs/images/readme-diagrams` 기본 asset-pair audit은 두 locale 모두 `ok=true`, `pair_count=120`, `missing_png=[]`, `missing_svg=[]`, `missing_refs=[]`, `svg_refs=[]`, `duplicate_refs={}`를 확인했다. 실제 module README는 새 대응 PNG 하나만 embed한다.

`--require-all-referenced` 전역 실행은 기존 공용 `docs/images/readme-diagrams`에 이미 존재하는 다른 119개 PNG가 해당 module README에 노출되지 않아 실패한다. 이는 이번 변경으로 생성된 pair의 결함이 아니라 저장소 전체 공용 asset 디렉터리의 기존 범위와 도구 옵션의 불일치다. 따라서 해당 strict count는 N/A로 기록하고, 이 모듈의 대응 pair/README 링크에 대해서는 기본 asset-pair audit을 통과 기준으로 사용했다.

Mermaid/Graphviz residue 및 README의 SVG 직접 embed는 없다.
