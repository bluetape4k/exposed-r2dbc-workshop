# Issue 115 Chapter 13 Diagram Review

## Scope

- SVG: `docs/assets/readme-diagrams/issue-115-chapter13-ecosystem-architecture-01.svg`
- PNG: `docs/assets/readme-diagrams/issue-115-chapter13-ecosystem-architecture-01.png`
- README embeds: `13-ecosystem-integrations/README.md`, `13-ecosystem-integrations/README.ko.md`
- Diagram type: architecture. No sequence diagram was created, so sequence-only participant, lifeline, label, branch, and palette gates are not applicable.

## Source And Reference Inputs

- README/source scope: Chapter 13 README pair and all Chapter 13 Kotlin examples/tests were inspected for BigQuery dry-run, Trino session options, CockroachDB retry, StarRocks OLAP local projection, and DuckDB embedded analytics boundaries.
- Repo-local architecture reference opened full-size: `docs/assets/readme-diagrams/issue-114-chapter12-parity-architecture-01.png`
- Best-practices architecture reference opened full-size: `/Users/debop/work/bluetape4k/bluetape4k-wiki/docs/diagrams/best-practices/assets/bluetape4k-leader-architecture-01.png`

## Evidence Ledger

| Gate | Evidence | Result |
|---|---|---|
| SVG XML parse | `xml=ok connectors=12 card_rects=15 icons=10` | PASS |
| PNG render | `~/.local/bin/cairosvg docs/assets/readme-diagrams/issue-115-chapter13-ecosystem-architecture-01.svg -o docs/assets/readme-diagrams/issue-115-chapter13-ecosystem-architecture-01.png -s 2`; PNG `3400 x 2380` | PASS |
| Full-size PNG eye inspection | Opened final PNG at full size after final coordinate change; text fits, cards are distinct from canvas, icon/text spacing is clear, no visual connector crossing or card intrusion, dashed arrowheads render solid and line-colored | PASS |
| Architecture style parity | Compared against the repo-local Chapter 12 architecture PNG and best-practices leader architecture PNG; final asset keeps light canvas, handwritten title, lane/card family, muted green/amber connector palette, and reader-facing guardrail footer | PASS |
| Connector audit | `diagram-connector-audit.py`: `PASS markers=2 connectors=12 cards=15 intrusions=0 crossings=0` | PASS |
| Geometry audit | `diagram-geometry-audit.py --fail-diagonal`: `geometry_failures=0` | PASS |
| Endpoint audit | `diagram-endpoint-audit.py`: `PASS files=1` | PASS |
| Mixed-corner audit | `diagram-mixed-corner-audit.py`: `PASS files=1 paths=12 q_bends=0 failures=0`; all connectors are straight same-row horizontal edge-to-edge connectors, so rounded-bend geometry is not applicable | PASS |
| Marker color and dash parity | `marker_color_checked=12 dashed_connectors=6 marker_failures=0`; marker child fill/stroke matches connector stroke and dashed arrowhead paths force `stroke-dasharray:none` | PASS |
| Catalog icon use | `images=10 data_bluetape4k_icon=10 missing_icon_attr=0 use_or_legacy_wrappers=0`; ids: `database/h2.svg`, `google-cloud/bigquery.svg`, `testcontainers/database/cockroach-labs.svg`, `testcontainers/database/duckdb.svg`, `testcontainers/database/starrocks.svg`, `testcontainers/database/trino.svg` | PASS |
| Row alignment | `inter_column_gaps=165/165 row_span_center=850.0 canvas_center=850.0`; lane inner gaps: `source=30/40`, `local=55/55`, `external=40/30` | PASS |
| Solid/dashed legend | SVG contains `Solid green` and `Dashed amber`; README pair repeats the connector meaning next to the image | PASS |
| Repo-local QA wrapper | `repo_local_diagram_wrapper=absent scripts_dir_exists=no`; fallback reference audits and custom invariants above provide concrete counts | PASS |

