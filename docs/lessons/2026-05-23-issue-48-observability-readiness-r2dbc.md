# Issue 48 Observability Readiness R2DBC

## Context

Chapter 12 needed matching Spring WebFlux and Ktor observability/readiness
examples that stay close to the existing production-integration slices. The
feature adds request correlation, structured error request ids, diagnostic
operation persistence, and live database readiness checks to both stacks.

## Lessons

- Treat `TimeoutCancellationException` as a readiness timeout, not as ordinary
  coroutine cancellation. Catch it before `CancellationException` when a bounded
  health ping should degrade instead of bubbling.
- Keep synthetic diagnostic delay outside `suspendTransaction`; only persist
  the completed operation row after measuring duration.
- Use the same durable table shape across Spring and Ktor examples so the
  workshop compares framework boundaries instead of domain model drift.
- README diagrams for this repository should be committed as PNG assets under
  `docs/assets/readme-diagrams/`, with SVG sources kept beside them when useful.

## Verification

```bash
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks
```

The targeted H2 run executed 31 tests successfully after the Spring/Ktor
diagnostic endpoints and request-correlation tests were added.
