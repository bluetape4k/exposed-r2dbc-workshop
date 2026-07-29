# Issue #44 Production Architecture 교훈

## 맥락

Issue #44는 chapter 12 Spring Boot 4와 Ktor R2DBC 예제가 단순히 동작하는 endpoint 묶음이 아니라 실제 application architecture baseline을 보여 주어야 했다.

## 결정

Issue #43의 two-module design을 유지하고 각 module 안에 package-level architecture boundary를 추가한다.

- Spring: `app`, `persistence`, `web`.
- Ktor: `app`, `config`, `persistence`, `routes`, `outbound`.

두 module은 Exposed R2DBC `suspendTransaction` 주변에서 같은 repository/service contract shape를 유지하고, HTTP와 error-mapping layer는 각 stack에 맞게 남긴다.

## 방어선

향후 chapter 12 child issue는 logic을 application entrypoint로 다시 합치지 말고 이 package slice를 확장해야 한다. README diagram은 `docs/images/readme-diagrams/` 아래 committed PNG asset을 사용해야 한다.
