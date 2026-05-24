# README Diagram Style Refresh

## Context

The README diagram set mixed Mermaid blocks, ASCII package/layout diagrams, old
open-arrow SVG markers, and sequence diagrams with very tall internal spacing.
The requested target style was the refreshed chapter 10 sample: component-panel
composition, smaller filled triangular arrows, and `Architects Daughter`
headings.

## Decision

Regenerate all committed README diagram SVG/PNG pairs through one style pass.
Sequence diagrams use a compact renderer that keeps a visible outer frame and
reduces participant/message spacing. Mermaid and ASCII diagram blocks in README
files were replaced with PNG-backed diagrams.

## Outcome

All README diagram references now point to PNG assets. The repository has no
remaining README Mermaid or `text` diagram fences. Existing SVG sources remain
beside their PNGs for future regeneration.

## Verification

- Rendered 105 SVG files to PNG with `rsvg-convert`.
- Validated 105 SVG files with `xmllint --noout`.
- Checked README PNG references: 0 missing.
- Confirmed README Mermaid fences: 0.
- Confirmed README `text` diagram fences: 0.
- Visually inspected representative architecture and sequence diagrams.

## Future Guidance

When adding README diagrams, commit SVG and PNG together, reference only PNG
from README files, use smaller filled triangular arrows, and keep sequence
diagrams compact before review.
