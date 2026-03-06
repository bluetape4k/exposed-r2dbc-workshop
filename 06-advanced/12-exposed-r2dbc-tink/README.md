# 12 Exposed R2DBC Tink (Google Tink 기반 암호화)

이 모듈은 **Google Tink** 암호화 라이브러리를 Exposed R2DBC와 통합하여 데이터베이스 컬럼을 투명하게 암호화/복호화하는 방법을 학습합니다.

Tink는 Google이 개발한 고수준 암호화 라이브러리로, 안전한 암호화 알고리즘을 쉽게 사용할 수 있도록 설계되었습니다. 이 모듈은 두 가지 암호화 방식을 지원합니다:

- **DAEAD** (Deterministic Authenticated Encryption with Associated Data): 결정적 암호화로 동일 평문이 항상 동일 암호문을 생성 → `WHERE` 절 검색 가능
- **AEAD** (Authenticated Encryption with Associated Data): 비결정적 암호화로 매번 다른 암호문을 생성 → 보안 강도가 더 높지만 `WHERE` 절 검색 불가

## 학습 목표

- Tink DAEAD/AEAD 암호화 컬럼 정의 방법 이해
- 문자열 및 바이너리 데이터의 투명한 암호화 및 복호화 수행
- DAEAD 결정적 암호화로 암호화된 컬럼을 `WHERE` 절에서 직접 쿼리
- AEAD 비결정적 암호화와 DAEAD 결정적 암호화의 트레이드오프 이해

## 핵심 개념

### DAEAD vs AEAD

| 방식        | 결정성   | WHERE 검색 | 보안 강도 | 권장 용도                     |
|-----------|-------|----------|-------|---------------------------|
| **DAEAD** | 결정적   | 가능       | 보통    | 검색 가능한 개인정보 (이름, 이메일 등)   |
| **AEAD**  | 비결정적  | 불가       | 높음    | 검색 불필요한 민감 데이터 (주소, 비밀번호) |

### 컬럼 타입

| 함수                                              | 암호화 방식  | 저장 타입      | 설명                          |
|-------------------------------------------------|---------|------------|-----------------------------|
| `tinkDaeadVarChar(name, length)`                | DAEAD   | `VARCHAR`  | 결정적 암호화 문자열 컬럼 (검색 가능)      |
| `tinkAeadVarChar(name, length, algorithm)`      | AEAD    | `VARCHAR`  | 비결정적 암호화 문자열 컬럼 (검색 불가)     |
| `tinkAeadBinary(name, length, algorithm)`       | AEAD    | `VARBINARY`| 비결정적 암호화 바이너리 컬럼 (검색 불가)    |

### 지원 알고리즘 (`TinkAeads`)

| 알고리즘                          | 설명                                   |
|-------------------------------|--------------------------------------|
| `TinkAeads.AES256_GCM`        | AES-256 GCM 모드 (기본값) - 범용 고성능 암호화    |
| `TinkAeads.CHACHA20_POLY1305` | ChaCha20-Poly1305 - 소프트웨어 기반 고성능 암호화 |

## 예제 개요

### `TinkColumnTypeTest.kt`

Exposed DSL에서 Tink 암호화 컬럼의 CRUD 동작과 검색 가능성을 검증합니다.

#### 테이블 정의 예시

```kotlin
val stringTable = object: IntIdTable("string_table") {
    // DAEAD: 결정적 암호화 → WHERE 절 검색 가능, 인덱스 활용 가능
    val name    = tinkDaeadVarChar("name", 255).nullable().index()
    val city    = tinkDaeadVarChar("city", 255).nullable().index()

    // AEAD: 비결정적 암호화 → WHERE 절 검색 불가, 보안 강도 높음
    val address = tinkAeadBinary("address", 255, TinkAeads.AES256_GCM).nullable()
    val age     = tinkAeadVarChar("age", 255, TinkAeads.CHACHA20_POLY1305).nullable()
}
```

#### 테스트 시나리오

| 테스트                                            | 설명                                                     |
|------------------------------------------------|--------------------------------------------------------|
| `문자열에 대해 암호화,복호화 하기`                           | INSERT → SELECT 시 투명한 복호화 확인, DAEAD 검색 가능/AEAD 검색 불가 확인 |
| `암호화된 컬럼을 Update 하기`                           | UPDATE 후 암호화된 값이 정상적으로 갱신되는지 확인                         |
| `nullable encrypted columns keep null values` | nullable 암호화 컬럼에 `null` 값이 그대로 보존되는지 확인                 |

## 코드 예제

### 1. 테이블 정의

```kotlin
import io.bluetape4k.exposed.core.tink.tinkAeadBinary
import io.bluetape4k.exposed.core.tink.tinkAeadVarChar
import io.bluetape4k.exposed.core.tink.tinkDaeadVarChar
import io.bluetape4k.tink.aead.TinkAeads

object UserSecrets: IntIdTable("user_secrets") {
    // DAEAD: 이메일은 로그인 검색이 필요하므로 결정적 암호화 사용
    val email = tinkDaeadVarChar("email", 255).index()

    // AEAD: 주소는 검색 불필요 → 비결정적 암호화로 더 높은 보안
    val address = tinkAeadVarChar("address", 512, TinkAeads.AES256_GCM).nullable()

    // AEAD Binary: 바이너리 데이터 암호화
    val profileImage = tinkAeadBinary("profile_image", 65535).nullable()
}
```

### 2. 데이터 삽입 (암호화 투명 처리)

```kotlin
val id = UserSecrets.insertAndGetId {
    it[email] = "user@example.com"
    it[address] = "서울특별시 강남구 테헤란로 123"
    it[profileImage] = imageBytes
}
```

### 3. 조회 (복호화 투명 처리)

```kotlin
val row = UserSecrets.selectAll().where { UserSecrets.id eq id }.single()

row[UserSecrets.email]   // "user@example.com" (자동 복호화)
row[UserSecrets.address] // "서울특별시 강남구 테헤란로 123" (자동 복호화)
```

### 4. DAEAD 컬럼으로 WHERE 검색 (결정적 암호화만 가능)

```kotlin
// DAEAD 컬럼은 암호화된 상태로 동등 비교가 가능합니다
val user = UserSecrets.selectAll()
    .where { UserSecrets.email eq "user@example.com" }
    .single()

// AEAD 컬럼은 매번 다른 암호문이 생성되어 WHERE 검색이 불가합니다
UserSecrets.selectAll()
    .where { UserSecrets.address eq "서울특별시 강남구 테헤란로 123" }
    .toList()   // 항상 빈 결과 반환
```

## DAEAD vs AEAD 선택 기준

```
검색이 필요한가?
    YES → DAEAD (tinkDaeadVarChar)
           예: 이름, 이메일, 전화번호, 주민번호 앞자리
    NO  → AEAD (tinkAeadVarChar / tinkAeadBinary)
           예: 비밀번호 힌트, 전체 주소, 카드번호, 생체정보
```

> **참고**: DAEAD는 동일 평문이 항상 동일 암호문을 생성하므로 빈도 분석 등의 통계적 공격에 취약합니다. 검색이 꼭 필요한 경우에만 사용하세요.

## 다른 암호화 모듈과 비교

| 모듈                         | 암호화 방식 | WHERE 검색 | 라이브러리       |
|----------------------------|--------|----------|-------------|
| `01-exposed-r2dbc-crypt`   | 비결정적   | 불가       | Bouncy Castle |
| `10-exposed-r2dbc-jasypt`  | 결정적    | 가능       | Jasypt      |
| `12-exposed-r2dbc-tink`    | 결정적/비결정적 선택 가능 | DAEAD만 가능 | Google Tink |
| `06-exposed-r2dbc-custom-columns` | 커스텀 구현 | 구현에 따라 다름 | AES 직접 구현 |

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :12-exposed-r2dbc-tink:test

# 특정 테스트 클래스 실행
./gradlew :12-exposed-r2dbc-tink:test --tests "exposed.r2dbc.examples.tink.TinkColumnTypeTest"

# H2만 사용하는 빠른 테스트
./gradlew :12-exposed-r2dbc-tink:test -PuseFastDB=true
```

## 참고 자료

- [Google Tink 공식 문서](https://developers.google.com/tink)
- [Exposed Crypt](https://debop.notion.site/Exposed-Crypt-1c32744526b0802da419d5ce74d2c5f3)
- [Exposed Jasypt](https://debop.notion.site/Exposed-Jasypt-1c32744526b080f08ab2f3e21149e9d7)
