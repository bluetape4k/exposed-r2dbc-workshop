# Chapter 10 Diagram Style Refresh 교훈

## 맥락

Chapter 10 multi-tenant strategy image는 preferred chapter 12 component arrangement와 맞지 않는 pipeline-style layout을 사용했다. 요청된 style은 chapter 10/11의 `Architects Daughter` typography를 유지하되, arrow를 더 작은 marker가 있는 filled triangular arrowhead로 바꾸는 것이었다.

## 결정

Chapter 10 strategy SVG/PNG를 두 개의 큰 framework panel(`Spring WebFlux tenant boundary`, `Ktor tenant boundary`)과 shared R2DBC contracts band로 다시 생성한다. README path는 안정적으로 유지한다.

## 결과

`docs/images/readme-diagrams/10-multi-tenant-strategy-map-01.png`는 이제 preferred panel composition, 더 작은 triangular arrow, Architects Daughter heading으로 chapter 10을 표현한다.

## 검증

- `rsvg-convert`로 PNG를 render했다.
- Rendered PNG를 visually inspected했다.
- `git diff --check`를 실행했다.

## 향후 지침

Chapter-level architecture diagram은 먼저 chapter 12 panel layout에 맞춘 뒤, central guide의 workspace typography와 arrow rule을 적용한다.
