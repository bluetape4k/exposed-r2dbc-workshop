# Diagram Checklist Audit

Scope: exposed-r2dbc-workshop README diagram assets, checked with the current `bluetape4k-diagram` checklist and rendered visual scans.

## Gates

- XML: `xmllint --noout` on every SVG.
- Connector: `diagram-connector-audit.py` on every SVG.
- Geometry: `diagram-geometry-audit.py` on every SVG.
- Mixed corners: `diagram-mixed-corner-audit.py` on every SVG.
- Sequence family: `diagram-sequence-style-audit.py` on every `*sequence*` / `*flow*` SVG.
- Render: `~/.local/bin/cairosvg -s 2` regenerated each changed PNG.
- Visual: rendered contact sheets in `docs/review/contact-sheets/` plus original-size spot checks for high-risk sequence/connector assets.

## Result

PASS for all audited SVGs and rendered PNGs. SVG marker definitions use explicit `userSpaceOnUse` arrowheads, rendered PNG arrowheads were checked in the visual pass, and sequence diagrams use visible participant, activation, label, and numbered-message signals.

## Changed SVG inventory

- `docs/images/readme-diagrams/00-shared-exposed-r2dbc-shared-class-02.svg` — PASS
- `docs/images/readme-diagrams/00-shared-exposed-r2dbc-shared-erd-01.svg` — PASS
- `docs/images/readme-diagrams/00-shared-exposed-r2dbc-shared-sequence-03.svg` — PASS
- `docs/images/readme-diagrams/01-spring-boot-spring-webflux-exposed-class-02.svg` — PASS
- `docs/images/readme-diagrams/01-spring-boot-spring-webflux-exposed-erd-01.svg` — PASS
- `docs/images/readme-diagrams/01-spring-boot-spring-webflux-exposed-sequence-03.svg` — PASS
- `docs/images/readme-diagrams/03-exposed-r2dbc-basic-exposed-r2dbc-sql-example-class-02.svg` — PASS
- `docs/images/readme-diagrams/03-exposed-r2dbc-basic-exposed-r2dbc-sql-example-erd-01.svg` — PASS
- `docs/images/readme-diagrams/03-exposed-r2dbc-basic-exposed-r2dbc-sql-example-sequence-03.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-01-connection-class-02.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-01-connection-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-02-ddl-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-02-ddl-class-01.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-02-ddl-erd-03.svg` — PASS
- `docs/images/readme-diagrams/04-exposed-r2dbc-ddl-02-ddl-sequence-04.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-01-dml-class-02.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-01-dml-erd-01.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-01-dml-sequence-03.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-02-types-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-02-types-class-01.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-03-functions-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-03-functions-class-02.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-04-transactions-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-04-transactions-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/05-exposed-r2dbc-dml-04-transactions-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-01-exposed-r2dbc-crypt-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-01-exposed-r2dbc-crypt-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-02-exposed-r2dbc-javatime-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-02-exposed-r2dbc-javatime-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-03-exposed-r2dbc-kotlin-datetime-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-03-exposed-r2dbc-kotlin-datetime-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-03-exposed-r2dbc-kotlin-datetime-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-04-exposed-r2dbc-json-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-04-exposed-r2dbc-json-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-04-exposed-r2dbc-json-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-05-exposed-r2dbc-money-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-05-exposed-r2dbc-money-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-05-exposed-r2dbc-money-erd-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-06-exposed-r2dbc-custom-columns-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-06-exposed-r2dbc-custom-columns-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-06-exposed-r2dbc-custom-columns-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-07-exposed-r2dbc-custom-entities-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-07-exposed-r2dbc-custom-entities-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-07-exposed-r2dbc-custom-entities-erd-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-07-exposed-r2dbc-custom-entities-sequence-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-08-exposed-r2dbc-jackson-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-08-exposed-r2dbc-jackson-erd-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-08-exposed-r2dbc-jackson-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-09-exposed-r2dbc-fastjson2-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-09-exposed-r2dbc-fastjson2-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-09-exposed-r2dbc-fastjson2-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-11-exposed-r2dbc-jackson3-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-11-exposed-r2dbc-jackson3-class-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-11-exposed-r2dbc-jackson3-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-12-exposed-r2dbc-tink-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-12-exposed-r2dbc-tink-class-02.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-12-exposed-r2dbc-tink-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/06-advanced-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/07-jpa-convert-01-convert-jpa-basic-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/07-jpa-convert-01-convert-jpa-basic-class-02.svg` — PASS
- `docs/images/readme-diagrams/07-jpa-convert-01-convert-jpa-basic-erd-03.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-02-exposed-r2dbc-virtualthreads-basic-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-02-exposed-r2dbc-virtualthreads-basic-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-02-exposed-r2dbc-virtualthreads-basic-class-03.svg` — PASS
- `docs/images/readme-diagrams/08-r2dbc-coroutines-02-exposed-r2dbc-virtualthreads-basic-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/09-spring-05-exposed-r2dbc-repository-coroutines-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/09-spring-05-exposed-r2dbc-repository-coroutines-class-01.svg` — PASS
- `docs/images/readme-diagrams/09-spring-05-exposed-r2dbc-repository-coroutines-erd-03.svg` — PASS
- `docs/images/readme-diagrams/09-spring-05-exposed-r2dbc-repository-coroutines-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-05.svg` — PASS
- `docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-class-02.svg` — PASS
- `docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/10-multi-tenant-03-multitenant-spring-webflux-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/10-multi-tenant-03-multitenant-spring-webflux-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/10-multi-tenant-03-multitenant-spring-webflux-class-03.svg` — PASS
- `docs/images/readme-diagrams/10-multi-tenant-03-multitenant-spring-webflux-sequence-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-02-cache-strategies-r2dbc-architecture-02.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-02-cache-strategies-r2dbc-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-02-cache-strategies-r2dbc-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-02-cache-strategies-r2dbc-class-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-03-routing-datasource-architecture-03.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-03-routing-datasource-architecture-04.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-03-routing-datasource-architecture-05.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-03-routing-datasource-class-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-03-routing-datasource-sequence-02.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-04-cache-strategies-ktor-r2dbc-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-05-cache-strategies-ktor-r2dbc-coroutines-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-06-routing-datasource-ktor-r2dbc-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-architecture-01.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-class-02.svg` — PASS
- `docs/images/readme-diagrams/11-high-performance-sequence-03.svg` — PASS

## 2026-07-05 full re-audit after reviewer geometry report

Scope: all `docs/images/readme-diagrams/*.svg` assets, including ERD relationship-state checks, sequence style checks, and connector endpoint/geometry checks.

| Gate | Evidence | Result |
| --- | --- | --- |
| XML parse | `find docs/images/readme-diagrams -name '*.svg' -print0 | xargs -0 -n1 xmllint --noout` | PASS, 97 SVGs |
| Full connector checklist | `/tmp/run_diagram_audit.sh .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, `failures=0` |
| Sequence best-practices style | `diagram-sequence-style-audit.py` over `*sequence*.svg` and `*flow*.svg` | PASS, `sequence_files=23` |
| ERD relationship information | `/tmp/erd_relationship_audit.py .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, `total=10 fail=0`; independent-table ERDs now state `No FK relationship` explicitly |
| Class arrowheads | `/tmp/class_arrow_audit.py .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, no markerless directional class paths |
| Rendered PNG spot inspection | Full-size PNGs opened for high-risk samples: `06-advanced-05-exposed-r2dbc-money-erd-03.png`, `11-high-performance-06-routing-datasource-ktor-r2dbc-architecture-01.png` | PASS |

Fixes applied in this pass:

- Added reader-facing `No FK relationship` notes to independent-table ERDs.
- Rerouted the three Ktor R2DBC high-performance architecture connectors that had non-orthogonal or diagonal endpoints.

## 2026-07-05 grid-layout rebuild and sequence palette pass

Scope: reviewer-requested full repair for ERD/Class grid layouts and Sequence best-practices palette/style across the current PR branch.

| Gate | Evidence | Result |
| --- | --- | --- |
| Grid ERD rebuild | Regenerated `07-jpa-convert-01-convert-jpa-basic-erd-03` and `09-spring-05-exposed-r2dbc-repository-coroutines-erd-03`; each SVG rerendered with `~/.local/bin/cairosvg -s 2` | PASS |
| ERD cardinality | `python3 /tmp/erd_cardinality_audit3.py .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, `total=10 fail=0` |
| ERD label placement | `python3 /tmp/erd_label_bounds_audit.py .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, `labels=11 fail=0` |
| Class grid rebuild | Regenerated `09-spring-07-spring-suspended-cache-class-02` from the old grid shape into a grouped class view with no hidden grid wiring or card-through connectors | PASS |
| Class connector audit | `diagram-connector-audit.py $(grep 'class.*\.svg$' /tmp/diagram-scope.txt)` | PASS, `class_connector_failures=0` |
| Sequence palette/style | Normalized all exposed-r2dbc-workshop sequence diagrams to the muted best-practices palette and rerendered PNGs | PASS |
| Sequence style audit | `diagram-sequence-style-audit.py $(grep 'sequence.*\.svg$' /tmp/diagram-scope.txt)` | PASS, combined `sequence_files=54` |
| Sequence connector audit | `diagram-connector-audit.py $(grep 'sequence.*\.svg$' /tmp/diagram-scope.txt)` | PASS, `seq_connector_failures=0` |
| Full wrapper | `/tmp/run_diagram_audit.sh .../exposed-r2dbc-workshop/.worktrees/docs-diagram-checklist-audit` | PASS, `failures=0` |
| Rendered visual inspection | Full-size PNGs opened: `07-jpa-convert-01-convert-jpa-basic-erd-03.png`, `09-spring-05-exposed-r2dbc-repository-coroutines-erd-03.png`, `09-spring-05-exposed-r2dbc-repository-coroutines-sequence-02.png`, `09-spring-07-spring-suspended-cache-class-02.png`; contact sheet `/tmp/diagram-final-contact-sheet.png` inspected | PASS |
| Diff hygiene | `git diff --check` | PASS |

Notes:

- ERDs now show explicit `1 : N`, `1 : 1`, `0..1 : N`, or an explicit independent/no-FK note so the reader can distinguish relationships from sample tables.
- The repository-coroutines Movie/Actor bridge is centered as a two-leg 1:N relation instead of a confusing N:M table placement.
- Class assets that carried old grid/card-through wiring were rebuilt as grouped class views; relationship-bearing schema details are shown in the paired ERD/sequence assets.

## 2026-07-06 final one-by-one audit

Scope: all final README diagram SVG/PNG assets under `docs/assets/readme-diagrams` and `docs/images/readme-diagrams`, using the corrected diagrams in this branch as the baseline for style, palette, arrowhead, connector, and layout consistency.

| Gate | Evidence | Result |
| --- | --- | --- |
| File-by-file ledger | `docs/review/2026-07-06-diagram-one-by-one-ledger.tsv` | PASS, 114 diagrams, 0 failures |
| XML parse | `xmllint --noout` per SVG | PASS, 114 SVGs |
| Forbidden worklog/source notes | strict text scan for source/worklog/validation-note wording | PASS, 0 matches |
| Dashed arrowheads | strict scan for dashed connector elements using marker arrowheads | PASS, 0 matches |
| Connector audit | `diagram-connector-audit.py` per SVG and aggregated over all SVGs | PASS, 114 SVGs |
| Geometry audit | `diagram-geometry-audit.py --fail-diagonal` per SVG and aggregated over all SVGs | PASS, `geometry_failures=0` |
| Endpoint audit | `diagram-endpoint-audit.py` per SVG and aggregated over all SVGs | PASS, 114 SVGs |
| Mixed-corner audit | `diagram-mixed-corner-audit.py` per SVG and aggregated over all SVGs | PASS, `paths=829 q_bends=192 failures=0` |
| Sequence style audit | `diagram-sequence-style-audit.py` over every `*sequence*` / `*flow*` SVG | PASS, `sequence_files=23` |
| Render | `~/.local/bin/cairosvg -s 2` per SVG | PASS, 114 PNGs regenerated |
| Visual scan | Final contact sheets opened: root/assets, chapters 00-06, chapters 07-11 | PASS, no visible overlaps, card-through lines, dashed arrowhead drift, or sequence palette drift |
| Diff hygiene | `git diff --check` | PASS |

Chapter 13 is intentionally absent in this repository: the README learning path and module directories currently end at `12-production-integration`, and no `13-*` README diagram assets exist.
