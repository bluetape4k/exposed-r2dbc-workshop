# README Diagram Style Refresh 교훈

## 맥락

README diagram set에는 Mermaid block, ASCII package/layout diagram, old open-arrow SVG marker, 내부 spacing이 매우 큰 sequence diagram이 섞여 있었다. 요청된 target style은 refreshed chapter 10 sample과 같은 component-panel composition, 더 작은 filled triangular arrow, `Architects Daughter` heading이었다.

## 결정

Committed README diagram SVG/PNG pair 전체를 한 번의 style pass로 다시 생성한다. Sequence diagram은 visible outer frame을 유지하면서 participant/message spacing을 줄이는 compact renderer를 사용한다. README file 안의 Mermaid와 ASCII diagram block은 PNG-backed diagram으로 교체한다.

## 결과

모든 README diagram reference는 이제 PNG asset을 가리킨다. Repository에는 남은 README Mermaid 또는 `text` diagram fence가 없다. 기존 SVG source는 향후 regeneration을 위해 PNG 옆에 유지한다.

## 검증

- SVG file 105개를 `rsvg-convert`로 PNG에 render했다.
- SVG file 105개를 `xmllint --noout`으로 검증했다.
- README PNG reference 확인: 0 missing.
- README Mermaid fence 확인: 0.
- README `text` diagram fence 확인: 0.
- Representative architecture와 sequence diagram을 visually inspected했다.

## 향후 지침

README diagram을 추가할 때는 SVG와 PNG를 함께 commit하고, README에서는 PNG만 reference하며, 더 작은 filled triangular arrow를 사용하고, review 전에 sequence diagram을 compact하게 유지한다.
