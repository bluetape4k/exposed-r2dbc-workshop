# Issue #149 README Pair Parity Verification

## 범위

이번 한국어 재작성 train에서 `README.md`와 `README.ko.md`로 함께 관리되는 파일은
primary rewrite 대상이 아니다. 이 문서는 bilingual pair가 깨지지 않았는지 확인하고,
이후 comment/KDoc 한국어화 작업이 README 구조를 변경하지 않도록 기준을 남긴다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| README pair 수 | 45 |
| `README.md`만 있고 `README.ko.md`가 없는 항목 | 0 |
| primary rewrite 대상 여부 | 제외 |
| parity-only 상태 | 유지 |

## 확인 명령

```bash
python3 - <<'PY'
from pathlib import Path
pairs = []
missing = []
for path in sorted(Path('.').rglob('README.md')):
    if any(part in {'.git', '.gradle', 'build', '.omx', '.worktrees'} for part in path.parts):
        continue
    korean = path.with_name('README.ko.md')
    if korean.exists():
        pairs.append((path, korean))
    else:
        missing.append(path)
print('pairs', len(pairs))
print('missing', len(missing))
PY
```

## 결정

- README bilingual pair는 이번 train의 단일 언어 문서 rewrite 범위에서 제외한다.
- 이후 PR에서 README를 수정해야 한다면 English/Korean pair를 함께 갱신한다.
- 단일 언어 문서와 Kotlin/KTS comment/KDoc 한국어화는 이 parity 기준을 훼손하지 않는다.
