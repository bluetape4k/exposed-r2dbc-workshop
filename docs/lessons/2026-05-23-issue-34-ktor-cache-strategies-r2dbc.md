# Issue #34 Ktor R2DBC Cache Strategies

## Decision

The Ktor cache strategies module uses a Redisson `RMap` plus explicit Exposed
R2DBC table operations instead of Spring cache abstractions. Route responses
carry `cacheStatus` so tests assert cache behavior directly instead of using
timing as a proxy.

## Notes

- Use a per-test cache name to avoid leaking Redis keys across reused
  Testcontainers Redis instances.
- Keep counters application-local. They are workshop diagnostics, not
  production metrics.
- Keep #69 separate for cancellation-aware population, single-flight loading,
  and concurrent suspend-call behavior.
