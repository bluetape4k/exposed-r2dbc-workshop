# README Diagram Infographic 전환

## 맥락

README 파일은 architecture, class, sequence, ERD 등 여러 diagram에 Mermaid code block을 사용했다. Workspace 전반의 visual direction이 reviewed pastel infographic PNG로 바뀌었고, 재사용을 위해 SVG source asset도 함께 보관하기로 했다.

## 결정

README Mermaid block을 생성된 PNG image link로 교체하고, 대응하는 SVG source를 PNG 옆에 저장한다. Diagram text는 English-only로 유지하고, 큰 label에는 Architects Daughter, 세부 text에는 Comic Mono를 쓰며, architecture/class/sequence/ERD diagram별 layout을 적용한다.

## 결과

README diagram은 `bluetape4k.github.io/docs/readme-diagram-samples`의 2026-05-19 shared style guide로 render된다. Root README asset은 repo-local asset placement rule이 있을 때 이를 따른다.

## 검증

Cross-repository conversion pass에서 `rsvg-convert`로 PNG/SVG asset을 생성하고 README link를 확인했다.

## 향후 지침

README diagram은 편집 가능한 SVG source와 함께 PNG embed로 유지한다. Visual consistency가 중요할 때 raw Mermaid나 단순 Mermaid theme recoloring으로 되돌리지 않는다.
