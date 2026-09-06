# Issue #232 구현 계획

## 완료 조건

- [ ] 두 순수 test consumer가 public `bluetape4k-exposed-r2dbc-tests:2.0.0`을
  사용한다.
- [ ] private fixture import/base class가 두 consumer에서 제거된다.
- [ ] SQL example은 private shared dependency도 제거하고, DDL connection은
  shared production fixture 때문에 test-scoped dependency를 유지한다.
- [ ] public alias가 local catalog에 추가되고 stable Bluetape BOM 정책과
  일치한다.
- [ ] H2 targeted tests와 test-only/runtime graph 검증을 통과한다.
- [ ] 호환성 차이와 repository-wide 후속 범위를 한국어 문서와 PR DoD에
  기록한다.

## 단계

1. 각 module의 현재 test imports와 dependency scope를 재확인한다.
2. catalog alias를 추가하고 두 build script의 `testImplementation`만 교체한다.
3. 테스트를 먼저 public package로 바꾼다.
   - `R2dbcExposedSQLExample` 및 `Schema`의 TestDB/withTables/base class
   - `Ex01_Connection` 및 H2 connection tests의 TestDB/withTables/base class
4. H2 targeted test를 module별로 순차 실행하고 public artifact resolved version을
   `dependencyInsight`로 확인한다.
5. runtime graph에서 public fixture와 Testcontainers가 production scope로
   새로 유입되지 않는지 확인한다.
6. 문서/lesson/PR evidence를 작성하고 독립 review를 거친다.

## 롤백

catalog alias와 두 build script/import 변경만 되돌리면 private shared test
fixture 소비로 복귀한다. production source와 shared module은 수정하지 않는다.

## 위험과 대응

- 공개 fixture의 environment property 이름이 private와 다르다: 기본 H2만
  실행하고 차이를 문서화한다.
- public artifact의 TestDB 목록이 private보다 작다: 선택 module에 사용되지
  않는 dialect만 제외한다.
- shared dependency가 production config 때문에 필요한 다른 module은 이번
  PR에 포함하지 않는다.
