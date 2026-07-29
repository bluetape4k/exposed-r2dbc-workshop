# Issue 89 Example Parity Diagrams 교훈

## 맥락

Issue #89는 duplicate implementation issue를 피하면서 `exposed-r2dbc-workshop` example을 `exposed-workshop`과 맞추도록 요청했다. 사용자는 README explanation이 rendered PNG asset인 overall Architecture Diagram을 우선해야 한다고 명확히 했다.

## 결정

Root README file에는 concept-level parity map으로 parity를 문서화하고, root ASCII architecture overview를 rendered runtime architecture PNG로 교체한다. Chapter 11과 12는 이미 PNG diagram을 사용했지만 chapter 10에는 overview diagram이 없었으므로 chapter-level PNG strategy map을 추가한다.

## 결과

README set은 이제 same-topic gap을 exact module name이 아니라 concept로 추적한다고 말한다. Connection-factory-per-tenant 같은 R2DBC-only example은 platform-specific으로 남고, DAO/entities와 transaction template 같은 JDBC-only example은 `exposed-workshop`에 남는다.

## 검증

- SVG source를 `rsvg-convert`로 PNG에 render했다.
- Root와 chapter README image reference를 확인했다.
- `git diff --check`를 실행했다.

## 향후 지침

Workshop README work에서는 SVG source가 함께 있는 PNG architecture diagram을 선호한다. Table은 diagram을 보조하는 용도로만 사용하고 primary explanation으로 삼지 않는다.
