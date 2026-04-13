# 02-cache-strategies-r2dbc README 다이어그램 개선 설계

- 날짜: 2026-04-13
- 대상: `11-high-performance/02-cache-strategies-r2dbc/README.md`
- 범위: README 안의 기존 Mermaid 다이어그램 개선 + architecture diagram 1개 추가
- 범위 제외: `README.ko.md` 신규 작성/동기화, 코드 변경, 기능 변경

## 목표

GitHub Markdown에서 바로 렌더되는 Mermaid를 사용해, 이 모듈의 핵심 학습 포인트를 더 빠르게 이해할 수 있도록 README의 다이어그램 품질을 높인다.

이번 작업의 우선순위는 다음과 같다.

1. 캐시 조회 흐름을 한눈에 이해할 수 있어야 한다.
2. CacheRepository 계층 구조와 각 전략의 책임이 명확해야 한다.
3. 아키텍처 큰 그림은 보조적으로 제공하되, 본문 핵심을 방해하지 않아야 한다.

## 현재 상태 요약

현재 README에는 다음 Mermaid 다이어그램이 이미 있다.

1. `classDiagram`
   - `AbstractR2dbcRedissonRepository<K, V>`와 세 개의 구체 Repository를 보여준다.
   - 전략 정보는 들어 있지만, 역할 구분과 시각적 강조가 약하다.

2. `flowchart`
   - L1 Near Cache → L2 Redis → DB 조회 흐름을 설명한다.
   - 핵심 경로는 맞지만, HIT/MISS/FILL 경로의 대비와 계층 구분이 더 선명해질 필요가 있다.

README에는 아직 이 모듈의 전체 실행 맥락(WebFlux Controller → CacheRepository → Cache Layer → Exposed/R2DBC → DB)을 한 장으로 보여주는 architecture diagram이 없다.

## 설계 원칙

- README 우선: GitHub에서 바로 렌더되는 Mermaid만 사용한다.
- 전달력 우선: UML 엄밀성보다 독자가 빠르게 이해하는 구성을 우선한다.
- 과밀도 방지: 클래스 메서드 전체를 나열하지 않고 대표 책임만 남긴다.
- 색상과 그룹화 우선: Cache Layer, Repository Layer, Runtime Layer, Persistence Layer를 색상과 subgraph로 구분한다.
- 기존 설명 존중: 기존 섹션 구조를 최대한 유지하고, 다이어그램만 더 읽기 좋게 다듬는다.

## 제안 다이어그램 구성

### 1. Architecture diagram 추가

목적:
- 이 모듈의 큰 그림을 먼저 보여준다.
- WebFlux + Coroutines + Cache + Redis + R2DBC 관계를 한 장으로 정리한다.

표현 범위:
- Client
- WebFlux Controller
- Suspended Cache Repository
- Caffeine Near Cache
- Redisson / Redis MapCache
- Exposed / R2DBC Transaction
- Database

표현 방식:
- `flowchart` 또는 `graph TD`
- `subgraph`로 Application, Cache Layer, Persistence Layer를 구분
- 요청/응답 경로와 저장/조회 경로를 분리

배치 의도:
- 위에서 아래로 요청이 흐르도록 배치
- 중앙에 CacheRepository를 놓고, 좌우/하단으로 L1, L2, DB를 배치해 “허브” 역할을 강조

### 2. 캐시 조회 흐름 다이어그램 개선

목적:
- 가장 중요한 학습 포인트인 cache hit/miss 동작을 직관적으로 보이게 한다.

개선 방향:
- L1(Caffeine), L2(Redisson), DB를 서로 다른 색상으로 구분
- `HIT`, `MISS`, `FILL` 레이블을 눈에 잘 띄게 정리
- DB miss 후 L2 저장, L1 warm-up 후 반환까지의 흐름을 명시
- 응답 속도 감각(빠른 경로 / 느린 경로)을 짧은 텍스트로 유지

유지할 내용:
- `get(key)` 시작점
- L1 → L2 → DB 순서
- `suspendTransaction` 기반 DB 조회
- TTL 적용

단순화할 내용:
- 구현 세부 API 호출은 넣지 않는다.
- 예외 흐름이나 eviction 흐름은 이 다이어그램에 포함하지 않는다.

### 3. CacheRepository class diagram 개선

목적:
- 세 개의 CacheRepository가 어떤 전략과 책임을 가지는지 빠르게 파악하게 한다.

개선 방향:
- 추상 베이스 Repository와 세 구현체의 상속 관계는 유지
- 각 구현체는 다음 정도만 표시
  - 전략명
  - 주 책임
  - 대표 operation
- 색상으로 역할을 구분
  - `UserCacheRepository`: Read/Write Through + Near Cache
  - `UserCredentialsCacheRepository`: Read-Only
  - `UserEventCacheRepository`: Write-Behind

단순화 원칙:
- 문서용 다이어그램이므로 모든 메서드를 나열하지 않는다.
- 제네릭/세부 시그니처는 이해에 필요한 수준만 남긴다.
- 클래스 크기를 줄여 README에서 한 번에 읽히도록 한다.

## README 배치 계획

권장 순서:

1. 기술 스택
2. Architecture diagram
3. CacheRepository class diagram
4. 캐시 조회 흐름
5. 프로젝트 구조 및 상세 설명

이 순서는 “큰 그림 → 구조 → 동작”의 읽기 흐름을 만든다.

## 문구 보강 계획

다이어그램 아래에는 긴 설명 대신 1~3줄 설명만 둔다.

예시 방향:
- Architecture diagram 아래: 이 모듈이 WebFlux + Coroutines 환경에서 2계층 캐시와 R2DBC를 어떻게 연결하는지 설명
- Class diagram 아래: 세 Repository가 어떤 캐시 전략을 담당하는지 설명
- Flow diagram 아래: 요청이 L1/L2/DB를 통과하며 적재되는 과정 설명

## 비목표

이번 작업에서는 다음을 하지 않는다.

- PlantUML 원본 관리
- SVG/PNG 렌더 산출물 추가
- README 전면 개편
- 성능 수치나 벤치마크 설명 재작성
- 실제 코드 구조 변경

## 검증 계획

문서 변경 후 다음을 확인한다.

1. Mermaid 코드 블록이 GitHub 친화적인 문법인지 확인
2. README 렌더 관점에서 문법 오류가 없는지 확인
3. 다이어그램 설명이 실제 코드 구조와 충돌하지 않는지 확인
4. 필요 시 해당 모듈 테스트를 실행하고 결과를 testlog에 기록

## 사용자 결정 기록

- 대상은 우선 `11-high-performance/02-cache-strategies-r2dbc` 한 모듈만 진행한다.
- GitHub 렌더링 호환성을 우선한다.
- README 다이어그램은 Mermaid로 유지한다.
- 가장 중요한 다이어그램은 `캐시 조회 흐름`과 `CacheRepository class diagram`이다.
- `README.ko.md`는 이번 단계에서 생성하지 않고, README.md만 먼저 다룬다.

## 승인 후 구현 범위

승인되면 다음 순서로 구현한다.

1. `README.md`의 기존 class diagram 재작성
2. `README.md`의 기존 cache flow diagram 재작성
3. architecture diagram 신규 추가
4. 다이어그램 주변 설명 문장 최소 보강
5. 문법/렌더 관점 검토
6. 테스트 실행 및 testlog 기록
