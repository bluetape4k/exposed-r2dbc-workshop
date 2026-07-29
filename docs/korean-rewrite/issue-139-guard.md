# Issue #139 Localization Guard

## Decision

이번 slice는 후속 한국어 재작성 PR에서 반복 사용할 guard를 추가한다. 아직 번역 전이므로 기본 실행은 scope boundary와 README pair만 실패 조건으로 삼고, `--strict-korean` 모드는 #161 최종 audit에서 남은 영어 후보를 실패로 승격하는 용도로 둔다.

## Commands

```bash
python3 scripts/localization/audit_scope.py
python3 scripts/localization/localization_guard.py
python3 scripts/localization/localization_guard.py --strict-korean
```

## Default Guard

기본 guard는 다음 조건을 검증한다.

- `README.md`와 `README.ko.md`는 같은 디렉터리에서 쌍으로 존재해야 한다.
- `AGENTS.md`, `CLAUDE.md`, `.github/**`, `.omc/**`, `docs/korean-rewrite/**`는 primary rewrite scope에 들어가면 안 된다.
- `README.md`/`README.ko.md` pair는 primary rewrite scope에 들어가면 안 된다.
- stale-English 후보 수를 report로 출력하되, 번역 전 train에서는 실패로 처리하지 않는다.

## Strict Korean Mode

`--strict-korean`은 primary docs와 Kotlin/KTS comment 후보에서 한국어가 섞이지 않은 영어 후보 라인을 count한다. 최종 #161에서는 필요한 allowlist 또는 threshold를 명시한 뒤 이 모드를 실패 조건으로 사용한다.

## Known Limits

이 guard는 정적 휴리스틱이다. code fence, inline code, identifier, command, URL, SQL/JSON/YAML literal처럼 영어 보존이 필요한 표면은 후속 PR review에서 source-aware 판단을 함께 적용해야 한다.
