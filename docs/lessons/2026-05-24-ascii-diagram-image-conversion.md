# ASCII Diagram Image Conversion 교훈

## 맥락

README diagram refresh 이후에도 여러 workshop README에 ASCII/Unicode diagram이 남아 있었다. 이 block들은 한국어와 영어 label을 섞고 있었고, README content에서는 rendered SVG/PNG diagram asset을 사용한다는 workspace rule도 따르지 않았다.

## 결정

설명용 ASCII diagram을 `docs/images/readme-diagrams/` 아래 committed SVG source가 뒷받침하는 PNG embed로 바꾼다. 같은 concept를 이미 다루는 diagram asset은 재사용하고, 누락된 diagram만 새로 생성한다.

## 결과

- Advanced, JPA convert, coroutine, Spring, multi-tenant, high-performance README pair에 남아 있던 diagram-like ASCII block을 변환했다.
- SVG/PNG diagram asset pair 9개를 추가했다.
- Korean JPA convert README의 Blog ERD section이 class diagram asset이 아니라 ERD asset을 가리키도록 고쳤다.

## 검증

- Diagram-like ASCII fence scan: `count=0`.
- README image link scan: `missing=0`, `localSvgLinks=0`.
- New SVG XML parse and PNG existence check: `NEW_DIAGRAMS_OK`.
- `git diff --check`: clean.
- Visual montage reviewed at `/tmp/exposed-r2dbc-diagram-montage.png`.

## 향후 규칙

README ASCII diagram을 교체할 때 image text는 영어로 유지하고, SVG와 PNG를 모두 commit하며, 새 asset을 만들기 전에 concept-equivalent existing asset 재사용을 우선한다.
