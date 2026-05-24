# ASCII Diagram Image Conversion

## Context

Several workshop READMEs still contained ASCII/Unicode diagrams after the README
diagram refresh. These blocks mixed Korean and English labels and did not follow
the workspace rule of using rendered SVG/PNG diagram assets in README content.

## Decision

Replace explanatory ASCII diagrams with PNG embeds backed by committed SVG
sources under `docs/images/readme-diagrams/`. Reuse existing diagram assets when
they already covered the same concept, and generate only the missing diagrams.

## Outcome

- Converted remaining diagram-like ASCII blocks in advanced, JPA convert,
  coroutine, Spring, multi-tenant, and high-performance README pairs.
- Added nine SVG/PNG diagram asset pairs.
- Fixed the Korean JPA convert README so the Blog ERD section points to the ERD
  asset instead of the class diagram asset.

## Verification

- Diagram-like ASCII fence scan: `count=0`.
- README image link scan: `missing=0`, `localSvgLinks=0`.
- New SVG XML parse and PNG existence check: `NEW_DIAGRAMS_OK`.
- `git diff --check`: clean.
- Visual montage reviewed at `/tmp/exposed-r2dbc-diagram-montage.png`.

## Future Rule

When replacing README ASCII diagrams, keep image text in English, commit both SVG
and PNG, and prefer reusing an existing concept-equivalent asset before creating
a new one.
