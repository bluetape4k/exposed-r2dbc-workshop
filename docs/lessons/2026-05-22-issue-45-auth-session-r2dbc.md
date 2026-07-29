# Issue #45 Auth Session R2DBC 교훈

## 맥락

Issue #45는 issue #43과 #44의 two-module package-slice design을 보존하면서 chapter 12 production integration module 안에 paired Spring Boot 4와 Ktor authentication/session example을 넣어야 했다.

## 결정

Authentication/session은 기존 Spring/Ktor production module 안의 package-level behavior로 유지한다.

- Spring은 repository-backed `ReactiveUserDetailsService`와 함께 WebFlux Security HTTP Basic을 사용한다.
- Ktor는 Basic authentication으로 database-backed session row를 만들고, 저장된 token hash와 대조해 signed `production_session` cookie를 검증한다.
- 두 stack 모두 BCrypt password hash와 SHA-256 session-token hash를 저장한다. Raw session token은 creation time에만 반환하고 list response에서는 숨긴다.
- Public registration은 permission/role을 `work:create`와 `USER`로 clamp한다. Admin/outbound access는 seeded admin account에서만 나온다.

## 방어선

향후 chapter 12 slice는 topic-specific Gradle project를 추가하지 말고 기존 module과 README pair를 계속 확장해야 한다. Auth 변경에서는 missing credentials, invalid credentials, authorized access, role denial, public registration permission/role clamping, session listing의 raw-token suppression을 test로 계속 다뤄야 한다.
