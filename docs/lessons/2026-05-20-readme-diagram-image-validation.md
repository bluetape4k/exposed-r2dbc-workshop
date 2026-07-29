# README Diagram Image 검증

## 맥락

README diagram asset은 reviewed pastel infographic style로 재생성됐다.

## 결정

README에는 matching SVG source가 있는 PNG embed를 유지한다. ERD rendering은 Mermaid `init`과 theme configuration block을 무시해야 한다. Class diagram은 visible inheritance와 realization stem을 보존해야 한다.

## 결과

`exposed-r2dbc-workshop` README diagram asset은 README link를 바꾸지 않고 재생성됐다. Visual spot check는 architecture, ERD, class, sequence diagram을 포함했다.

## 검증

- 전체 재생성: `rendered=172`, `missing=[]`.
- README image link: `missing=0`.
- Local SVG image embed: `0`.
- Mermaid residue: `0`.
- Asset count: `png=86`, `svg=86`.
- Shape sanity check: `shapeCandidates=0`.
- `init`/theme ERD residue: `0`.
- Whitespace check: `git diff --check`.

## 향후 지침

R2DBC workshop diagram에서는 sequence diagram이 label을 담을 만큼 충분히 높아야 하며, PR을 열기 전에 class diagram에서 arrow stem이 보이는지 검증한다.

## 2026-05-20 ERD Routing 후속 작업

`09-spring-05-exposed-r2dbc-repository-coroutines-erd-03`에는 Mermaid parser residue box와 모호한 relationship line이 있었다. 현재 `MovieSchema` source를 기준으로 image를 다시 만들어 `actors_in_movies`가 bridge table로 표시되고 `movieId`, `actorId` FK arrow가 명시되도록 했다.

향후 ERD는 parser residue를 제거하고 bridge-table FK를 bridge에서 parent table로 이어지는 짧은 orthogonal lane으로 routing해야 한다.
