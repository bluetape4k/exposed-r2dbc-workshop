# Issue #44 Production Architecture

## Context

Issue #44 needed the chapter 12 Spring Boot 4 and Ktor R2DBC examples to show a
real application architecture baseline, not only a working endpoint bundle.

## Decision

Keep the two-module design from issue #43 and add package-level architecture
boundaries inside each module:

- Spring: `app`, `persistence`, and `web`.
- Ktor: `app`, `config`, `persistence`, `routes`, and `outbound`.

Both modules keep the same repository/service contract shape around Exposed
R2DBC `suspendTransaction`, while the HTTP and error-mapping layers remain
stack-native.

## Guardrail

Future chapter 12 child issues should extend these package slices instead of
re-collapsing logic into the application entrypoint. README diagrams should use
committed PNG assets under `docs/assets/readme-diagrams/`.
