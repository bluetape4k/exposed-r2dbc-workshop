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
