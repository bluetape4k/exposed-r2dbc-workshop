# Issue #45 Auth Session R2DBC

## Context

Issue #45 needed paired Spring Boot 4 and Ktor authentication/session examples
inside the chapter 12 production integration modules, while preserving the
two-module package-slice design from issue #43 and #44.

## Decision

Keep authentication/session as package-level behavior inside the existing
Spring and Ktor production modules:

- Spring uses WebFlux Security HTTP Basic with a repository-backed
  `ReactiveUserDetailsService`.
- Ktor uses Basic authentication to create a database-backed session row, then
  validates a signed `production_session` cookie against the stored token hash.
- Both stacks persist BCrypt password hashes and SHA-256 session-token hashes.
  Raw session tokens are returned only at creation time and hidden from list
  responses.
- Public registration clamps permission/roles to `work:create` and `USER`;
  admin/outbound access comes only from the seeded admin account.

## Guardrail

Future chapter 12 slices should continue to extend the existing modules and
README pairs instead of adding topic-specific Gradle projects. For auth changes,
tests must keep covering missing credentials, invalid credentials, authorized
access, role denial, public registration permission/role clamping, and
raw-token suppression in session listings.
