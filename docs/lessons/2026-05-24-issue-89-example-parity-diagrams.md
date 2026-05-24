# Issue 89 Example Parity Diagrams

## Context

Issue #89 asked to align `exposed-r2dbc-workshop` examples with
`exposed-workshop` while avoiding duplicate implementation issues. The user
clarified that README explanation should prioritize overall Architecture
Diagrams rendered as PNG assets.

## Decision

Document parity in root README files with a concept-level parity map and replace
the root ASCII architecture overview with a rendered runtime architecture PNG.
Add a chapter-level PNG strategy map to chapter 10 because it had no overview
diagram while chapter 11 and chapter 12 already used PNG diagrams.

## Outcome

The README set now states that same-topic gaps are tracked by concept, not exact
module names. R2DBC-only examples such as connection-factory-per-tenant remain
platform-specific, and JDBC-only examples such as DAO/entities and transaction
template remain in `exposed-workshop`.

## Verification

- Rendered SVG sources to PNG with `rsvg-convert`.
- Checked root and chapter README image references.
- Ran `git diff --check`.

## Future Guidance

For workshop README work, prefer PNG architecture diagrams with adjacent SVG
sources. Use tables only to support the diagram, not as the primary explanation.
