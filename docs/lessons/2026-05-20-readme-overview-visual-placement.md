# 2026-05-20 — README overview visual 배치

## 맥락

README diagram과 chart는 장식용 generated asset이 아니라 source-backed documentation으로 다뤄야 한다. 이번 pass는 2026 reference document와 shared README diagram style guide를 사용했지만, module name과 grouping의 authority는 source code와 build layout에 두었다.

## 결정

Root README에 English-only SVG+PNG overview visual을 추가하고, overview diagram을 installation, usage, build instruction보다 앞에 배치한다. 기존 Architecture/Diagram section이 usage example 뒤에 붙어 있던 경우 위쪽으로 이동한다.

## 결과

`exposed-r2dbc-workshop`은 이제 root README overview diagram과 module composition chart를 갖고, README visual placement는 overview-first rule을 따른다. 생성된 label은 image 안에서 localized text를 피한다.

## 검증

- 생성된 SVG 파일을 `xmllint --noout`으로 parse했다.
- 생성된 PNG 파일을 `rsvg-convert`로 render했다.
- Workspace README image-link scan에서 missing local image가 0개였다.
- Workspace Architecture/Diagram ordering scan에서 Installation, Usage, Examples, Build heading 뒤에 남은 section이 0개였다.
- 생성된 root overview SVG text에 non-ASCII character가 없었다.

## 향후 메모

Architecture diagram을 README 끝에 덧붙이지 않는다. Overview 또는 architecture diagram은 상단 근처에 두고, class, sequence, ERD, flow diagram은 설명하는 section 옆에 배치한다.

Root overview diagram과 composition chart는 BOM이 있으면 맨 앞에 두고 Examples 또는 Additional examples가 있으면 맨 뒤에 둔다. 중간 group은 repo-specific README가 alphabetic grouping을 요구하지 않는 한 source-backed orientation order를 유지한다.
