# multitenant-spring-webflux README 다이어그램 개선 설계

- 날짜: 2026-04-13
- 대상: `10-multi-tenant/03-multitenant-spring-webflux/README.md`
- 범위: README 안의 Mermaid 다이어그램 개선 + architecture diagram 추가 + schema isolation 시각화 보강
- 범위 제외: `README.ko.md` 신규 작성/동기화, 코드 변경, 기능 변경

## 목표

GitHub Markdown에서 바로 렌더되는 Mermaid를 사용해, 이 모듈의 핵심 학습 포인트인 테넌트 전파 흐름과 schema-based 격리 구조를 더 직관적으로 이해할 수 있도록 README의 다이어그램 품질을 높인다.

## 우선순위

1. 요청 헤더에서 tenant가 어떻게 식별되고 ReactorContext/CoroutineContext를 통해 전파되는지 한눈에 보여야 한다.
2. schema-based 멀티테넌시가 동일 DB 인스턴스 안에서 어떻게 격리되는지 시각적으로 드러나야 한다.
3. 기존 sequenceDiagram은 유지하되 더 읽기 쉽게 정리한다.

## 현재 상태

- `README.md`만 존재하고 `README.ko.md`는 없다.
- 기존 Mermaid는 `### 멀티테넌시 요청 흐름`의 sequenceDiagram 1개다.
- schema-based 격리 구조는 ASCII 블록으로 설명되고 있다.

## 설계 원칙

- GitHub 호환성을 위해 Mermaid만 사용한다.
- 전달력 우선: 엄밀한 UML보다 요청 흐름과 격리 구조 이해를 우선한다.
- 기존 README 구조를 최대한 유지한다.
- 예제의 핵심인 Schema-based 방식만 다이어그램으로 강화하고, Row-based / Database-based는 텍스트 비교를 유지한다.

## 제안 다이어그램 구성

### 1. 아키텍처 개요 추가

포함 요소:
- HTTP Client
- TenantFilter
- ReactorContext / TenantId
- ActorController
- Repository
- `suspendTransactionWithCurrentTenant`
- Schema-based DB (`korean`, `english`)

목적:
- 헤더 기반 tenant 식별 → context 전파 → schema 전환 → 데이터 조회 흐름을 한 장으로 보여준다.

### 2. 멀티테넌시 요청 흐름 sequenceDiagram 개선

개선 방향:
- `TenantFilter`, `ReactorContext`, `Controller`, `Repository`, `DB`의 책임을 더 명확히 분리한다.
- `X-TENANT-ID` → `TenantId` 저장 → 현재 tenant 조회 → schema 설정 → repository 조회 순서를 더 읽기 쉽게 정돈한다.
- 필요 시 기본 tenant fallback을 짧게 표시한다.

### 3. Schema-based 격리 구조 Mermaid 시각화

현재 ASCII 블록을 Mermaid flowchart로 바꿔 다음을 강조한다.
- 하나의 DB 인스턴스 아래 `korean` / `english` schema 분리
- 각 schema 내부의 주요 테이블(`movies`, `actors`, `actors_in_movies`) 구분
- 데이터는 같은 앱에서 조회하지만 schema 단위로 격리됨

## README 권장 흐름

1. 기술 스택
2. 아키텍처 개요
3. 멀티테넌시 요청 흐름
4. 테넌트 정의
5. Schema-based 격리 구조
6. Row-based / Database-based 비교

## 비목표

- `README.ko.md` 생성
- Row-based / Database-based용 추가 Mermaid 다이어그램 작성
- 코드 구조 변경
- 테스트 코드 수정

## 검증 계획

1. Mermaid fenced block 문법 확인
2. sequence / flowchart가 GitHub 친화적인지 확인
3. README 설명과 실제 코드 구조 충돌 여부 확인
4. 문서 전용 변경이므로 테스트 기준선은 환경 제약 여부와 함께 최종 보고에 명시

## 사용자 결정 기록

- 이 모듈은 요청 흐름과 스키마 격리 구조를 둘 다 equally 중요하게 본다.
- README는 Mermaid 유지
- `README.ko.md`는 현재 없으며 이번 단계에서는 생성하지 않는다.
