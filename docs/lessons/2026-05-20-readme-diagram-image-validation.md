# README Diagram Image Validation

## Context

README diagram assets were regenerated with the reviewed pastel infographic
style.

## Decision

Keep PNG README embeds with matching SVG sources. ERD rendering must ignore
Mermaid `init` and theme configuration blocks. Class diagrams must preserve
visible inheritance and realization stems.

## Outcome

The exposed-r2dbc workshop README diagram assets were regenerated without
changing README links. Visual spot checks covered architecture, ERD, class, and
sequence diagrams.

## Verification

- Full regeneration: `rendered=172`, `missing=[]`.
- README image links: `missing=0`.
- Local SVG image embeds: `0`.
- Mermaid residue: `0`.
- Asset counts: `png=86`, `svg=86`.
- Shape sanity check: `shapeCandidates=0`.
- `init`/theme ERD residue: `0`.
- Whitespace check: `git diff --check`.

## Future Guidance

For R2DBC workshop diagrams, keep sequence diagrams tall enough for labels and
validate class diagrams for visible arrow stems before opening PRs.

## 2026-05-20 ERD Routing Follow-up

`09-spring-05-exposed-r2dbc-repository-coroutines-erd-03` had a Mermaid parser
residue box and ambiguous relationship lines. The image was rebuilt from the
current `MovieSchema` source so `actors_in_movies` is shown as the bridge table
with explicit `movieId` and `actorId` FK arrows.

Future ERDs should remove parser residue and route bridge-table FKs as short
orthogonal lanes from bridge to parent tables.
