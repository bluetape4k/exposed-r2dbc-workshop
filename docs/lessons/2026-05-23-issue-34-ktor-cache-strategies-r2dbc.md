# Issue #34 Ktor R2DBC Cache Strategies 교훈

## 결정

Ktor cache strategies module은 Spring cache abstraction 대신 Redisson `RMap`과 명시적인 Exposed R2DBC table operation을 사용한다. Route response는 `cacheStatus`를 포함하므로 tests는 timing을 proxy로 쓰지 않고 cache behavior를 직접 assert한다.

## 메모

- 재사용되는 Testcontainers Redis instance 사이에 Redis key가 새지 않도록 test마다 cache name을 분리한다.
- Counter는 application-local로 유지한다. 이는 workshop diagnostic이지 production metric이 아니다.
- Cancellation-aware population, single-flight loading, concurrent suspend-call behavior는 #69 범위로 분리한다.
