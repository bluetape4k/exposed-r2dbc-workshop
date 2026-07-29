# README Diagram Layout 수정

## 맥락

Follow-up visual QA에서 생성된 README diagram에 두 가지 layout defect가 발견됐다.

- 일부 architecture connector가 매우 짧은 line segment로 render되어 arrow head만 보였다.
- Sequence participant header label이 header box 위쪽으로 치우쳐 있었다.

관련 sequence issue도 함께 수정했다. Self-call이 이전에는 zero-length arrow로 render되어 독립된 arrow head처럼 보였다.

## 결정

기존 diagram style은 유지하고 생성된 SVG/PNG asset의 geometry만 갱신한다. Architecture connector line segment는 인접 card 사이의 visible gap을 가로질러야 한다. Sequence participant label은 architecture card와 같은 vertical-centering baseline을 사용해야 한다. Sequence self-call은 zero-length line 대신 작은 loop로 render한다.

## 검증

- README image link check: missing=0, localSvgImageLinks=0, mermaidResidue=0
- PNG/SVG shape check: shapeCandidates=0
- architecture short connector check: shortArch=0
- sequence header alignment check: seqTop=0
- sequence zero-length arrow check: zeroSeq=0
- `git diff --check`
- exposed root architecture와 representative sequence diagram의 visual sample을 검토했다.

## 향후 지침

SVG가 문법적으로 유효하더라도 arrow head만 보이는 connector는 rendering failure로 다룬다. Geometry check는 PR 생성 전에 architecture connector length, sequence header baseline, sequence self-call arrow를 포함해야 한다.
