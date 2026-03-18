# Changelog

모든 주요 변경사항은 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 이 프로젝트는 [유의적 버전](https://semver.org/lang/ko/)을 따릅니다.

---

## [Unreleased]

### Changed

- Bluetape4k `1.4.0` → `1.5.0-Beta1` 업그레이드
- `.claude/worktrees/` 경로 gitignore에 추가

---

## [1.1.1] - 2025-05-xx

### Fixed

- RedissonClient 설정 개선 (연결 안정성 향상)
- 간헐적 발생하는 시간차(timing) 테스트 실패 대응

### Changed

- Kotlin `2.3.20-RC3` → `2.3.20` (stable) 전환
- Gradle JVM 인수 수정: G1GC → 기본 GC로 변경
- `.editorconfig` 파일 추가 (코드 스타일 통일)
- `AGENTS.md` 저장소 작업 가이드 정비

---

## [1.1.0] - 2025-04-xx

### Added

- Kotlin `2.3.20-RC3` 지원
- `-Xcontext-parameters` 컴파일러 플래그 활성화
- 캐시 벤치마크 마크다운 워크플로 추가 (`feat: add cache benchmark markdown workflow`)

### Changed

- Kotlin `2.2.21` → `2.3.20-RC3` 업그레이드
- 의존성 대규모 업그레이드 (Spring Boot, Exposed, Bluetape4k 등)

### Fixed

- Testcontainers 2.x API 변경에 따른 수정

---

## [1.0.5] - 2025-03-xx

### Changed

- 빌드 안정화를 위해 Kotlin 버전 다운그레이드 (`2.3.10` → `2.2.21`)
- Bluetape4k `1.3.0` → `1.4.0` 업그레이드

### Documentation

- 루트 README.md 최종 보강 (아키텍처 다이어그램, Exposed v1 변경사항, 기여 가이드)
- 전체 모듈 KDoc 및 README 보강 (`00-shared`, `01-spring-boot`, `03~11` 모든 모듈)
- 프로젝트 전체 KDoc 최신화 및 코드 품질 개선

---

## [1.0.0] - 2025-02-xx

### Added

- 초기 릴리즈
- 전체 멀티 모듈 구성 (`00-shared` ~ `11-high-performance`)
- Testcontainers 기반 다중 DB 지원 (H2, PostgreSQL, MySQL 8, MariaDB)
- Spring WebFlux + Exposed R2DBC 통합 예제 (`01-spring-boot`)
- SQL DSL 기본, DDL, DML 예제 (`03`, `04`, `05` 모듈)
- 고급 기능: 암호화, JSON, Money, 커스텀 컬럼 (`06-advanced`)
- JPA → Exposed R2DBC 마이그레이션 패턴 (`07-jpa-convert`)
- Coroutines / Virtual Threads 예제 (`08-r2dbc-coroutines`)
- Spring Repository 패턴, Redis Suspended Cache (`09-spring`)
- Schema 기반 멀티테넌시 + WebFlux (`10-multi-tenant`)
- 캐시 전략, 읽기/쓰기 분리 라우팅 DataSource (`11-high-performance`)

[Unreleased]: https://github.com/bluetape4k/exposed-r2dbc-workshop/compare/1.1.1...HEAD

[1.1.1]: https://github.com/bluetape4k/exposed-r2dbc-workshop/compare/1.1.0...1.1.1

[1.1.0]: https://github.com/bluetape4k/exposed-r2dbc-workshop/compare/1.0.5...1.1.0

[1.0.5]: https://github.com/bluetape4k/exposed-r2dbc-workshop/compare/1.0.0...1.0.5

[1.0.0]: https://github.com/bluetape4k/exposed-r2dbc-workshop/releases/tag/1.0.0
