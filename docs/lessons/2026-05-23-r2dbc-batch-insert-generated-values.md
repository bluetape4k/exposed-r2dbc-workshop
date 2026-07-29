# R2DBC Batch Insert Generated Values 교훈

## 맥락

Workshop을 `bluetape4k-dependencies` 1.1.1로 전환한 뒤 PostgreSQL CI matrix가 `exposed-r2dbc-shared`에서 movie sample data를 seed하는 동안 실패했다. Exposed R2DBC는 `MovieTable.batchInsert(...)` 실행 중 auto-increment count mismatch를 보고했다.

## 결정

Sample data seed insert는 generated return value를 사용하지 않는다. 따라서 해당 `batchInsert` call에 `shouldReturnGeneratedValues = false`를 지정한다. 이는 bluetape4k Exposed repository의 다른 곳에서 쓰는 기존 high-throughput insert pattern과 일치한다.

## 결과

Targeted PostgreSQL shared test가 local에서 통과한다.

- `./gradlew :exposed-r2dbc-shared:test -PuseDB=POSTGRESQL --max-workers=1 --continue`

## 향후 지침

Generated ID를 소비하지 않는 R2DBC batch insert에서는 `shouldReturnGeneratedValues = false`를 명시한다. 특히 PostgreSQL에서 driver 및 dialect별 generated-key handling failure를 피할 수 있다.
