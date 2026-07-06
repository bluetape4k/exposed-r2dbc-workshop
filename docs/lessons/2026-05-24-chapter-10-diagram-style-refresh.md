# Chapter 10 Diagram Style Refresh

## Context

Chapter 10's multi-tenant strategy image used a pipeline-style layout that did
not match the preferred chapter 12 component arrangement. The requested style
kept the chapter 10/11 `Architects Daughter` typography and switched arrows to
filled triangular arrowheads with smaller markers.

## Decision

Regenerate the chapter 10 strategy SVG/PNG with two large framework panels
(`Spring WebFlux tenant boundary` and `Ktor tenant boundary`) plus a shared
R2DBC contracts band. Keep README paths stable.

## Outcome

`docs/images/readme-diagrams/10-multi-tenant-strategy-map-01.png` now presents
chapter 10 using the preferred panel composition, smaller triangular arrows,
and Architects Daughter headings.

## Verification

- Rendered the PNG with `rsvg-convert`.
- Visually inspected the rendered PNG.
- Ran `git diff --check`.

## Future Guidance

For chapter-level architecture diagrams, match chapter 12's panel layout first,
then apply the workspace typography and arrow rules from the central guide.
