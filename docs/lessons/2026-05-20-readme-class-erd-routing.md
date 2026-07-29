# README Class/ERD Routing 정리

## 맥락

README class와 ERD image는 bluetape4k workspace 전반에서 documentation, blog post, presentation에 재사용하기 위해 재생성됐다.

## 결정

Class와 ERD diagram에는 blocker-aware lane selection을 포함한 orthogonal connector routing을 사용한다. Pastel color와 기존 typography는 유지하되, cubic curve와 component interior를 가로지르는 connector path는 피한다.

## 결과

재생성된 class/ERD SVG는 relation-aware component placement, 직선 horizontal/vertical lane, 더 작은 arrow marker, top/bottom port를 사용한다. Connector는 vertical first/final segment를 갖고, horizontal lane은 component edge가 아니라 row midline 근처에 배치된다.

## 검증

- `node --check .omx/scripts/refine-readme-diagrams.mjs`
- 변경된 class/ERD SVG: cubic connector count `0`
- 변경된 class/ERD SVG: card-interior crossing candidates `0`

## 향후 지침

Diagram을 재생성할 때 blocker-aware route scoring을 보존하고, 넓은 image churn을 받아들이기 전에 contact sheet를 확인한다.
