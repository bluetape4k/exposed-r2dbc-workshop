# Lessons & Learns: Issue #36 Ktor R2DBC docs and verification

## Context

Issue #36 closes the Ktor R2DBC chapter 10 and 11 discoverability pass after the
Ktor multi-tenant, cache, coroutine cache, and routing datasource modules landed.

## Learnings

- `./gradlew projects` is the source of truth for runnable task prefixes. The
  Ktor examples use unique leaf project names such as `:07-multitenant-ktor` and
  `:06-routing-datasource-ktor-r2dbc`; prefixed names like
  `:exposed-r2dbc-11-high-performance-06-routing-datasource-ktor-r2dbc` are not
  valid in this repository.
- `Examples.yml` explicitly gates the new Ktor modules for touched PRs and
  pushes. Nightly covers them through the H2 full shard; non-H2 Nightly shards
  remain intentionally limited to modules with real multi-database surfaces.
- Root README discoverability should include direct Ktor links, not only the
  chapter indexes, so readers can find the Ktor equivalents without guessing the
  chapter layout.

## Future Guard

After adding or renaming an example module, verify every documented Gradle task
with `./gradlew <task> --dry-run` or `./gradlew <module>:tasks` before publishing
README updates.
