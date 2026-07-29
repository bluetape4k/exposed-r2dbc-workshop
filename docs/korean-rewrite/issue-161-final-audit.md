# 한국어 재작성 최종 감사

## 범위

- Epic: #137
- Subissues: #138-#161
- Stacked PR train: #162-#184
- 최종 감사 PR: #161 scope
- Primary rewrite 대상: 단일 언어 문서, Kotlin/KTS 주석, KDoc, 속성/함수 인자 설명
- Parity-only 대상: `README.md` / `README.ko.md` bilingual pairs
- 제외 대상: `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `.omc/skills/**`

## PR Train

| Issue | PR | Scope |
| --- | --- | --- |
| #138 | #162 | Scope inventory and exclusions |
| #139 | #163 | Localization guard and stale-English scan |
| #140 | #164 | Root single-language docs |
| #141 | #165 | Early lesson documents |
| #142 | #166 | Tenant and production lesson documents |
| #143 | #167 | Integration and performance lesson documents |
| #144 | #168 | Late lesson documents |
| #145 | #169 | Review documents |
| #146 | #170 | Superpowers foundation plans |
| #147 | #171 | Superpowers issue plans |
| #148 | #172 | Superpowers specs |
| #149 | #173 | Bilingual README parity audit |
| #150 | #174 | Shared test infrastructure comments |
| #151 | #175 | Spring WebFlux, basic, and DDL comments |
| #152 | #176 | DML select and insert comments |
| #153 | #177 | Remaining DML comments |
| #154 | #178 | Advanced date, time, and JSON comments |
| #155 | #179 | Advanced custom, money, and serialization comments |
| #156 | #180 | JPA conversion, coroutine, and Spring comments |
| #157 | #181 | Multi-tenant Spring comments |
| #158 | #182 | Multi-tenant Ktor and onboarding comments |
| #159 | #183 | High-performance comments |
| #160 | #184 | Production and ecosystem integration comments |
| #161 | #185 | Final audit and integration closeout |

## Verification

- `python3 scripts/localization/audit_scope.py`
  - 확인: excluded operating docs와 bilingual README pair가 primary rewrite 범위에서 분리됨.
- `python3 scripts/localization/localization_guard.py`
  - 확인: guard 실행 성공.
  - 참고: 남은 stale-English 후보는 SQL 예제, 식별자, 명령, URL, 정확한 오류 문자열, bilingual pair/감사 evidence에서 발생하는 후보로 취급함.
- `git diff --check`
  - 확인: whitespace 오류 없음.
- `./gradlew test -PuseDB=H2 --continue --console=plain`
  - 확인: 전체 H2 test task 성공, `BUILD SUCCESSFUL`.

## Decisions

- GitHub issue/PR metadata는 repo 규칙에 맞춰 English로 유지했다.
- LLM-facing operating docs는 instruction source이므로 한국어 재작성 범위에서 제외했다.
- README bilingual pairs는 이미 locale pair로 관리되므로 primary rewrite 대신 parity 검증 대상으로 유지했다.
- API route, serialized enum value, command, dependency name, SQL, HTTP status, exact error text는 contract로 보고 번역하지 않았다.
