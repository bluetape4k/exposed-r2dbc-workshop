# Dependency Catalog Upgrade 정리

## 맥락

`bluetape4k-dependencies`가 Apache Fory Dependabot PR을 중앙 dependency upgrade batch에 포함했다. 같은 batch에서 중앙 bluetape4k BOM constraint도 `1.8.1-SNAPSHOT` 계열로 이동했다.

## 결정

중앙 Fory Kotlin catalog version을 materialize하고, direct bluetape4k snapshot alias를 게시된 `1.8.1-SNAPSHOT` module에 맞춘다.

## 결과

`gradle/libs.versions.toml`은 Fory Kotlin `0.17.0`을 담고, direct bluetape4k alias는 `1.8.1-SNAPSHOT`을 기준으로 resolve된다.

## 검증

- `./gradlew build -x test --no-daemon`
- `./gradlew :02-cache-strategies-r2dbc:dependencyInsight --configuration testRuntimeClasspath --dependency bluetape4k-io --refresh-dependencies --no-daemon --no-configuration-cache`
- `./gradlew :02-cache-strategies-r2dbc:test -PuseDB=H2 --refresh-dependencies --no-daemon --no-configuration-cache`
