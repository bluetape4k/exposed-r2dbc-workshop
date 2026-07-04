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
