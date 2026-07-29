# Issue 49 Chapter 12 문서 검증 교훈

## 맥락

Chapter 12 examples는 issue #44-#48을 통해 점진적으로 추가됐다. 마지막 docs/verification pass에서는 root README link, chapter README verification note, GitHub Actions path filter가 서로 맞아야 했다.

## 교훈

- Focused example workflow는 root README file과 README diagram asset도 포함해야 한다. 이 파일들이 discoverability contract의 일부이기 때문이다.
- Chapter README verification section은 어떤 workflow가 fast example coverage를 맡고, 어떤 workflow가 repository-wide CI를 맡으며, Nightly가 module coverage를 어떻게 상속하는지 명시해야 한다.
- Auto-discovered example module은 documentation에 module list를 중복하지 말고 `./gradlew projects`로 검증한다.

## 검증

```bash
actionlint .github/workflows/Examples.yml
./gradlew projects
repo-test-summary -- ./gradlew :01-spring-production-integration:test :02-ktor-production-integration:test -PuseDB=H2 --continue --console=plain --no-build-cache --rerun-tasks
git diff --check
```
