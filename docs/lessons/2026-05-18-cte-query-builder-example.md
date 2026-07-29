# CTE Query Builder 예제

## 맥락

`bluetape4k-dependencies`가 `bluetape4k-exposed` `1.8.1-SNAPSHOT`을 관리하는 `1.0.1-SNAPSHOT`을 게시했다. 이제 R2DBC workshop은 managed dependency line을 통해 새 CTE Query Builder API를 사용할 수 있다.

## 교훈

R2DBC workshop 예제에서는 먼저 dependencies BOM을 갱신하고, version catalog alias가 게시된 artifact ID와 일치하는지 확인한다. 현재 Exposed artifact는 `bluetape4k-exposed-*` 이름을 사용한다.

## 증거

- 기존 raw SQL `Ex50_RecursiveCTE` 옆에 `Ex51_CteQueryBuilder`를 추가했다.
- `:01-dml:test` fast path에서 H2로 `CteTable`과 R2DBC `withCte`를 검증했다.

## 향후 방어선

BOM snapshot이 managed artifact name을 바꿀 때는 example code를 추가하기 전에 resolved POM과 catalog alias를 확인한다.
