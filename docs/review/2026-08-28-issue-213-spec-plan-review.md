# Issue #213 설계·구현 계획 통합 검토

## 검토 범위와 근거

- 대상: `docs/superpowers/specs/2026-08-28-issue-213-r2dbc-caffeine-design.md`
  (설계)와 `docs/superpowers/plans/2026-08-28-issue-213-r2dbc-caffeine-plan.md`
  (계획)
- 근거: live Issue #213, 중앙 catalog `1.4.0`, provider
  `bluetape4k-exposed` tag `1.12.1`, 현재 Chapter 11의 Ktor R2DBC sibling,
  provider의 `AbstractR2dbcCaffeineRepository`/`LocalCacheConfig` 구현
- 검토 방식: 여섯 관점(Performance, Stability, Security, Operator/Ops,
  Developer/API, User/caller)과 main-session 통합. 독립 lane 세 개는
  제한 시간 내 결과를 반환하지 않아 회수했으며, workflow fallback에 따라
  동일 범위를 main-session에서 재검토했다.

## 통합 결과

| Priority | Lens | Evidence | Required edit / disposition | Rerun lane |
|---|---|---|---|---|
| P2 | Performance | 설계 71–77행의 단일 요청 흐름과 125–137행의 bounded queue·drain 경계, 계획 65–75행의 timing risk | 교육용 H2 예제에는 벤치마크 수치를 추가하지 않는다. Step 4-P에서 round-trip, queue drain, allocation/blocking을 실제 테스트 결과로 확인하고 README에는 측정하지 않은 성능을 주장하지 않는다. | Performance (Step 4-P) |
| P2 | Stability | 설계 79–88행의 close 순서와 계획 156–160행의 manager 복구; startup 예외 정리는 이번 검토에서 설계 121–123행과 계획 183–187행에 명시 | 구현에서 initializer 실패 시 `resources.close()` 후 예외를 재던지고, cancellation·close·flush failure 테스트를 계획대로 실행한다. | Stability (Step 4-T/4-P) |
| P3 | Security | 설계 19–22행의 로컬 H2 범위, 계획 170–175행의 구조화 오류 응답 | 인증·인가·외부 secret은 교육용 로컬 서버 범위 밖이다. SQL은 Exposed 파라미터 바인딩을 사용하고 stack trace를 HTTP 응답에 넣지 않는 조건을 구현·검증한다. | Security (Step 6-R) |
| P2 | Operator/Ops | 설계 113–114행의 health 응답과 128–132행의 flush 오류/비보장; 계획 297–309행의 PR·merge hold | 운영 메트릭/롤백 runbook은 workshop 범위에 넣지 않는다. health의 `queueDepth`, `workerState`, `lastFlushError`와 README의 retry/durability 비보장을 실제 값으로 검증한다. | Ops (Step 6-R/7-R) |
| P1 | Developer/API | 기존 초안은 PUT body와 GET hit/miss 응답 형태가 불명확했음 | 설계 103–117행과 계획 170–179행에 `PUT /products/{sku}` body `{"name":"..."}`, `ProductRecord` 응답, `ProductReadResponse(product, cache)` 및 `HIT`/`MISS`를 고정했다. | Developer/API (재검토 완료) |
| P2 | User/caller | 설계 103–117행의 경로별 동작과 계획 105–119행의 misuse/failure 테스트 | 빈 이름·120자 초과·unknown SKU·path/body 중복 없는 요청을 400/404로 문서화하고 테스트한다. | User/caller (Step 6-R) |

P0=0, P1=0이다. P2는 구현·검증 단계에서 실행할 구체적인 rerun 지점과
범위를 가졌고, P3는 현재 범위의 명시적 제외 또는 검증 조건으로
처리했다. 설계의 API 보강은 승인된 목표(동일 key/value와 세 write mode)를
더 구체화하며 범위를 변경하지 않는다.

## Spec-to-plan traceability

| Spec acceptance criterion | Plan task / evidence |
|---|---|
| 동일 `ProductRecord` key/value와 세 write mode | Tasks 2–5, module tests |
| hit/miss, unknown, invalidate/clear, DB direct read | Tasks 2 and 4 |
| `READ_ONLY`/`WRITE_THROUGH`/`WRITE_BEHIND` 반영 시점 | Tasks 2, 4, 5 |
| cancellation과 repository → manager → pool close 순서 | Tasks 3–5 |
| admission rollback, drain, flush error, DB source-of-truth | Tasks 2 and 5 |
| catalog/project/workflow 등록 | Tasks 1 and 8 |
| module/chapter/root README 및 Korean KDoc | Task 6 |
| EN/KO architecture·sequence SVG/PNG pair와 audits | Task 7 |
| targeted/broader/static/actionlint 검증 | Task 8 and Task 9 |
| Lore commit, Korean PR metadata, exact-head CI, rebase merge hold | Tasks 9 and 10 |

## Writer gate

설계·계획·본 리뷰는 한국어 기술 문서로 작성했으며 다음을 확인했다.

| Gate | Result | Evidence |
|---|---|---|
| SPW-01 | PASS | live Issue/provider/catalog/sibling 근거와 범위·비보장 기록 |
| SPW-02 | PASS | 설계의 대안·경계·실패 모드·DoD, 계획의 ordered tasks·tests·rollback |
| SPW-03 | PASS | `korean-naturalness-checklist.md` KO-01..KO-06 수동 검토 |
| SPW-04 | PASS | spec-to-plan 표와 provider API/source anchor 대조 |
| SPW-05 | PASS | Markdown read-back, headings/tables/code fences 확인 |

기계 검증:

```text
node /Users/debop/.codex/skills/bluetape-writer/scripts/audit-korean-terms.mjs \
  docs/superpowers/specs/2026-08-28-issue-213-r2dbc-caffeine-design.md \
  docs/superpowers/plans/2026-08-28-issue-213-r2dbc-caffeine-plan.md
=> Terminology audit passed: 2 file(s), series=clinic-appointment, findings=0.
git diff --check
=> PASS
```

## Verdict

Step 2-R and Step 3-R: PASS. Implementation may start after committing the
spec, plan, and this review evidence. Performance/stability observations remain
explicit verification work; they are not claims that the final implementation
has already passed.
