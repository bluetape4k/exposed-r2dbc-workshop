# Class Diagram Hierarchy Audit 정리

## 맥락

Mermaid-to-SVG conversion이 inheritance 또는 implementation arrow가 parent contract를 향해 아래쪽으로 가리키는 layout을 보존하면 README class diagram이 오해를 만들 수 있다.

## 결정

Inheritance 또는 implementation edge가 있으면 interface, abstract, base contract node를 implementor 또는 subclass 위에 둔다. Open-triangle marker가 parent node에 닿도록 해당 edge를 orthogonal path로 다시 routing한다.

## 결과

Exposed R2DBC workshop README class diagram asset은 top-down으로 다시 layout됐고, 수정된 SVG source에서 PNG를 재생성했다.

## 검증

- Workspace class SVG 전체에서 downward `inheritLine`, `implLine` endpoint를 scan했다: `COUNT 0`.
- 변경된 PNG asset을 `rsvg-convert`로 다시 render했다.
- 변경된 SVG 파일을 `xmllint --noout`으로 검증했다.

## 향후 지침

README class diagram을 게시하기 전에 `docs/images/readme-diagrams/*class*.svg`에 대해 inheritance-direction scan을 실행하고, 변경된 diagram family마다 rendered PNG를 하나 이상 눈으로 확인한다.
