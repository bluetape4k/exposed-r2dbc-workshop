# Exposed Gradle Plugin 교훈

## 맥락

Main source에 table을 정의하는 R2DBC workshop module 전반에 JetBrains Exposed Gradle plugin을 도입했다.

## 결정

Workshop은 managed `bt4k` catalog가 아니라 기존 Exposed version alias에 묶인 repo-local plugin alias를 사용한다.

## 결과

Spring WebFlux, multi-tenant, cache, routing, Ktor, production example은 이제 explicit migration setting이 있는 `generateMigrations`를 노출한다.

## 검증

`git diff --check`, `./gradlew -q help`, `:spring-webflux-exposed:tasks --all`을 실행했다.

## 향후 방어선

Example runtime path가 R2DBC이더라도 plugin task discovery에는 H2 JDBC migration database를 사용한다.
