# Issue #138 Korean Rewrite Scope Inventory

## Decision

이번 한국어 재작성 train은 단일 언어 문서와 Kotlin/KTS 주석만 primary scope로 다룬다. GitHub issue/PR metadata, LLM-facing operating docs, skill guidance, 그리고 이미 `README.md`/`README.ko.md` 쌍으로 관리되는 bilingual README 파일은 primary rewrite 대상에서 제외한다.

## Scope Rules

- GitHub issue and PR titles/bodies stay English.
- `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `.omc/skills/**` stay English.
- `docs/korean-rewrite/**` audit artifacts are scope-control records, not primary rewrite targets.
- `README.md`/`README.ko.md` pairs are parity verification targets, not primary rewrite targets.
- In-scope repository prose and Kotlin/KTS comments/KDoc become Korean.
- Preserve identifiers, package names, API names, commands, URLs, versions, SQL/JSON/YAML literals, serialized names, and exact error text.
- Expand touched `@property`, `@param`, `@return`, and `@throws` entries with detailed Korean explanations for properties, parameters, returns, errors, cancellation, and timeout semantics where relevant.

## Reproducible Audit

```bash
python3 scripts/localization/audit_scope.py
```

Use the script output as the authoritative inventory when opening or reviewing the stacked PR slices for #139 through #161.

## Snapshot

The audit was taken from `develop` at `9ac94825527740d512617437f26b515349e14e5b`.

- Primary single-language documentation candidates: 87 files.
- Bilingual README pair files excluded from primary rewrite: 90 files.
- Operating/LLM-facing/scope-control docs excluded: `.github/copilot-instructions.md`, `.omc/skills/exposed-r2dbc/SKILL.md`, `AGENTS.md`, `CLAUDE.md`, `docs/korean-rewrite/issue-138-inventory.md`.
- Kotlin/KTS files: 505.
- Kotlin/KTS comment or KDoc candidate lines: 13,928.
- KDoc detail tag candidates: 140.

## Documentation Buckets

| Bucket | Files | Issue slice |
|---|---:|---|
| root single-language docs | 2 | #140 |
| `docs/lessons` | 46 | #141-#144 |
| `docs/review` | 4 | #145 |
| `docs/superpowers/plans` | 18 | #146-#147 |
| `docs/superpowers/specs` | 17 | #148 |
| README bilingual pairs | 90 | #149 parity only |

## Kotlin Comment Buckets

| Bucket | Kotlin/KTS files | Comment/KDoc candidate lines | Issue slice |
|---|---:|---:|---|
| `00-shared` | 26 | 650 | #150 |
| root/buildSrc Gradle KTS, `01-spring-boot`, `03-exposed-r2dbc-basic`, `04-exposed-r2dbc-ddl` | 46 | 1,335 | #151 |
| `05-exposed-r2dbc-dml/01-dml` | 29 | 4,182 | #152 |
| remaining `05-exposed-r2dbc-dml` modules | 24 | 1,795 | #153 |
| advanced date/time/JSON modules | 15 | 2,079 | #154 |
| remaining `06-advanced` modules | 33 | 1,808 | #155 |
| `07-jpa-convert`, `08-r2dbc-coroutines`, `09-spring` | 65 | 847 | #156 |
| Spring WebFlux multi-tenant modules | 110 | 610 | #157 |
| Ktor/onboarding multi-tenant modules | 43 | 39 | #158 |
| `11-high-performance` | 87 | 438 | #159 |
| `12-production-integration`, `13-ecosystem-integrations` | 27 | 145 | #160 |

## Validation Plan

- Run `python3 scripts/localization/audit_scope.py` after each broad scope change.
- Run `git diff --check` for every PR.
- Run targeted Gradle tests for each touched module.
- Run full final validation in #161 before reporting merge-ready.

## Issue Train

- Epic: #137
- Scope and guard: #138, #139
- Documentation rewrite: #140-#149
- Kotlin/KTS comments: #150-#160
- Final audit and integration closeout: #161
