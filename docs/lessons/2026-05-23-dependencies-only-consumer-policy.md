# Dependencies-only Consumer Policy 교훈

## 맥락

R2DBC workshop은 이미 `bluetape4k-dependencies`를 가져오고 있었지만, catalog가 일부 bluetape4k artifact를 직접 pin하고 있었다. Consumer repository 안에서 version ownership이 중복된 상태였다.

## 결정

`bluetape4k-dependencies`를 유일한 bluetape4k version source로 유지하고, 모든 bluetape4k artifact alias는 versionless로 둔다.

## 결과

Catalog에는 더 이상 직접 bluetape4k artifact version이나 별도 assertions alias가 없다. Artifact resolution은 이제 BOM을 따른다.

## 검증

Forbidden-reference grep, `git diff --check`, `./gradlew compileKotlin --no-daemon --no-configuration-cache`를 실행했다.

## 향후 지침

Ecosystem BOM이 바뀌면 먼저 BOM alias를 올리고, catalog의 bluetape4k artifact는 versionless 상태로 유지한다.
