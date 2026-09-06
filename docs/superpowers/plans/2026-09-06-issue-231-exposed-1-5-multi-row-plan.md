# Issue #231 구현 계획

## 완료 조건

- [x] Exposed `1.5.0` override와 stable Bluetape BOM `2.0.0` resolved graph를
  함께 증명한다.
- [x] multi-row VALUES 단일 statement, driver batch fallback, generated values,
  conflict/inserted-count 관계를 테스트한다.
- [x] README와 lesson에 driver별 제약과 generated-key workaround를 기록한다.
- [x] targeted tests와 diff 검사를 통과한다. 모듈별 `detekt` task는 존재하지
  않아 targeted Kotlin compilation/test를 static coverage로 사용한다.
- [ ] 한국어 PR 본문에 issue 연결과 DoD 증거를 채운다.

검증 증거: `:01-dml:test --tests "exposed.r2dbc.examples.dml.Ex02_Insert" -PuseDB=H2_PSQL`
에서 26개 실행(24 passed, 2 skipped), `dependencyInsight`에서 Exposed `1.5.0`
및 Bluetape BOM `2.0.0`을 확인했다. 이후 PostgreSQL은 동일한 targeted suite
27개 통과, MariaDB는 27개 중 22개 통과/5개 skip으로 완료했으며 새 부분 충돌
계약 테스트도 두 driver에서 통과했다. PR exact-head CI는 생성 후 확인한다.

## 단계

1. 현재 worktree/branch와 dependency graph를 확인한다.
2. `gradle/libs.versions.toml` 및 DML test configuration에 Exposed BOM `1.5.0`
   test override를 추가한다.
3. 테스트를 먼저 작성한다.
   - H2 PostgreSQL mode에서 `useMultiRowValues=true` SQL log에 `VALUES` tuple가
     둘 이상인지 확인
   - `useMultiRowValues=false` fallback 반환 행 수 확인
   - generated values 활성/비활성 결과 확인
   - duplicate conflict는 반환값을 driver 계약 이상으로 단정하지 않는지 확인
4. 테스트가 통과하면 Ex02 KDoc/README 예제를 실제 API 호출과 일치시킨다.
5. `./gradlew :01-dml:test --tests ...` 및 dependency insight/static analysis를
   실행한다. PostgreSQL/MariaDB Testcontainers는 직렬로 실행한다.
6. lesson/PR evidence를 작성하고 독립 review를 거친다.

## 롤백

Exposed BOM alias와 DML test override, 새 테스트/문서만 제거하면 기존
stable BOM과 기존 driver batch 예제로 즉시 복귀한다. `MovieSchema`는 변경하지
않으므로 seed 영향은 없다.

## 위험과 대응

- H2 logger SQL 표기가 dialect에 따라 달라질 수 있다: 키워드/tuple 수만
  검증하고 전체 문자열을 고정하지 않는다.
- multi-row generated keys는 driver별 지원 차이가 있다: happy path와
  `shouldReturnGeneratedValues=false`를 분리한다.
- Testcontainers가 로컬에서 불안정할 수 있다: H2를 먼저 실행하고 container
  결과는 별도 evidence로 기록한다.
