# Issue 49 Chapter 12 Docs Verification

## Context

Chapter 12 examples were added incrementally through issues #44-#48. The final
docs/verification pass needed to make root README links, chapter README
verification notes, and GitHub Actions path filters agree.

## Lessons

- Focused example workflows should include root README files and README diagram
  assets when those files are part of the discoverability contract.
- Keep chapter README verification sections explicit about which workflow owns
  fast example coverage, which workflow owns repository-wide CI, and how Nightly
  inherits module coverage.
- For auto-discovered example modules, verify `./gradlew projects` instead of
  duplicating the module list in documentation.

## Verification

```bash
actionlint .github/workflows/Examples.yml
./gradlew projects
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks
git diff --check
```
