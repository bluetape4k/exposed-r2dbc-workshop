# 01 Exposed R2DBC Crypt (투명한 컬럼 암호화)

이 모듈은 `exposed-crypt` 확장을 사용하여 데이터베이스 컬럼을 투명하게 암호화/복호화하는 방법을 학습합니다. 개인정보, 비밀, 금융 데이터 등 민감한 정보를 저장 시 보호하는 데 유용합니다.

## 학습 목표

- 암호화된 컬럼을 Exposed 테이블에 정의하는 방법 이해
- 다양한 암호화 알고리즘(`AES`, `Blowfish`, `Triple DES`) 사용법 학습
- DSL과 DAO 스타일 모두에서 암호화 컬럼 적용
- 암호화 컬럼 검색의 제한 사항 인식

## 핵심 개념

이 기능의 핵심은 암호화와 복호화를 자동으로 처리하는 커스텀 컬럼 타입입니다.

- `encryptedVarchar(name: String, colLength: Int, encryptor: Encryptor)`: 내용을 암호화된 문자열로 저장하는 `VARCHAR` 컬럼을 정의합니다.
- `encryptedBinary(name: String, colLength: Int, encryptor: Encryptor)`: 내용을 암호화된 바이너리 데이터로 저장하는 `VARBINARY` 또는
  `BYTEA` 컬럼을 정의합니다.
- `Encryptor`: 암호화/복호화 로직을 위한 인터페이스입니다. `org.jetbrains.exposed.v1.crypt.Algorithms` 객체에서 여러 구현체를 제공합니다.

**중요 사항**: Exposed의 기본
`Encryptor` 구현은 동일한 평문에 대해 매번 다른 암호문을 생성하는 알고리즘을 사용합니다. 이는 패턴 분석을 방지하기 위한 보안 기능입니다. 하지만 이러한 컬럼에서 직접 동등성 검사(
`where { table.column eq "value" }`)를 수행할 수 **없습니다**. 검색 가능한 암호화를 위해서는 Jasypt와 같은 결정적 암호화 알고리즘이 필요합니다 (
`10-exposed-jasypt` 예제 참조).

## 지원 암호화 알고리즘

`org.jetbrains.exposed.v1.crypt.Algorithms` 객체가 제공하는 알고리즘과 각 특성입니다.

| 알고리즘                            | 모드  | 비결정적 | 키 파라미터                    | 보안 수준 | 특징                            |
|----------------------------------|-----|--------|------------------------------|---------|-------------------------------|
| `AES_256_PBE_GCM(password, salt)` | GCM | 예     | 비밀번호 + 솔트 (8바이트 이상)       | 높음     | AEAD 인증 암호화, IV 포함            |
| `AES_256_PBE_CBC(password, salt)` | CBC | 예     | 비밀번호 + 솔트 (8바이트 이상)       | 중간     | 패딩 오라클 취약점 주의                |
| `BLOW_FISH(key)`                  | ECB | 예     | 키 (임의 길이, 최대 56바이트)       | 낮음     | 구형 알고리즘, 신규 프로젝트 사용 비권장      |
| `TRIPLE_DES(key)`                 | CBC | 예     | 키 (정확히 24바이트)              | 낮음     | 구형 알고리즘, 신규 프로젝트 사용 비권장      |

> **권장 알고리즘**: 신규 프로젝트에는 `AES_256_PBE_GCM`을 사용하세요. GCM 모드는 인증(AEAD)을 내장하여
> 암호문 변조를 감지할 수 있습니다.

## 보안 주의사항

> **경고**: 프로덕션 환경에서 암호화 컬럼을 사용할 때 아래 사항을 반드시 준수하세요.

1. **하드코딩 금지**: 예제 코드의 `"passwd"`, `"12345678"`, `"key"` 등은 테스트 전용입니다.
   실제 환경에서는 충분한 엔트로피를 가진 값을 환경 변수 또는 Vault, AWS KMS 같은 키 관리 시스템에서 주입하세요.

2. **솔트(salt) 관리**: AES PBE 계열은 솔트를 암호문에 포함하지 않으므로, 복호화에 동일한 솔트가 필요합니다.
   솔트를 분실하면 기존 데이터를 복호화할 수 없습니다.

3. **컬럼 크기 계산**: 암호화 후 Base64 인코딩으로 평문보다 길어집니다.
   `Encryptor.maxColLength(plaintextByteSize)` 를 이용해 컬럼 크기를 적절히 산정하세요.

4. **비결정적 암호화와 검색**: Exposed 기본 Encryptor는 비결정적(매번 다른 암호문)이므로 `WHERE` 절 검색이 불가합니다.
   암호화된 컬럼에 대한 검색이 필요하다면 결정적 암호화 모듈을 사용하세요:
   - `10-exposed-r2dbc-jasypt`: Jasypt 기반 결정적 암호화
   - `12-exposed-r2dbc-tink`: Google Tink 기반 결정적 암호화

5. **키 교체(Rotation)**: 키를 교체해야 할 경우 기존 데이터를 일괄 재암호화해야 합니다.
   운영 환경에서는 키 교체 절차를 사전에 계획하세요.

## 예제 개요

### `Ex01_EncryptedColumn.kt` (DSL 스타일)

DSL API를 사용한 암호화 컬럼의 기본 사용법을 보여줍니다.

- **테이블 정의**: 다양한 암호화 알고리즘(`AES_256_PBE_CBC`, `AES_256_PBE_GCM`, `BLOW_FISH`, `TRIPLE_DES`)을 사용하는 여러 컬럼이 있는 테이블 정의 방법
- **Insert & Update**: 값을 삽입하거나 업데이트하면 데이터베이스로 전송되기 전에 자동으로 암호화됩니다. 애플리케이션 코드는 평문만 다룹니다.
- **Select**: 데이터를 조회하면 컬럼 값이 자동으로 복호화됩니다.
- **검색 제한**: 암호화된 컬럼에서 `where` 절을 사용한 `select` 쿼리가 예상대로 작동하지 않음을 명시적으로 보여줍니다.

### `Ex02_EncryptedColumnWithEntity.kt` (DAO 스타일)

DAO API와 암호화 컬럼을 통합하여 엔티티처럼 사용하는 방법을 보여줍니다.

- **엔티티 정의**: `encryptedVarchar`와 `encryptedBinary` 컬럼에 매핑되는 속성을 가진 `IntEntity` 정의
- **CRUD 작업**: 엔티티 생성(`ETest.new { ... }`), 읽기(`ETest.all()`), 업데이트가 원활하게 작동합니다. 암호화와 복호화는 개발자에게 완전히 투명합니다.
- **검색 제한**: 암호화된 속성으로 엔티티 찾기(`ETest.find { TestTable.varchar eq "value" }`)가 실패함을 강조합니다.

## 코드 예제

### 1. 암호화 컬럼이 있는 테이블 정의 (DSL)

```kotlin
val nameEncryptor = Algorithms.AES_256_PBE_CBC("passwd", "5c0744940b5c369b")

object StringTable: IntIdTable("StringTable") {
  val name: Column<String> = encryptedVarchar("name", 80, nameEncryptor)
  val city: Column<String> =
    encryptedVarchar("city", 80, Algorithms.AES_256_PBE_GCM("passwd", "5c0744940b5c369b"))
  val address: Column<String> = encryptedVarchar("address", 100, Algorithms.BLOW_FISH("key"))
}
```

### 2. 엔티티에서 암호화 컬럼 사용 (DAO)

```kotlin
object TestTable: IntIdTable() {
  private val encryptor = Algorithms.AES_256_PBE_GCM("passwd", "12345678")
  val varchar = encryptedVarchar("varchar", 100, encryptor)
  val binary = encryptedBinary("binary", 100, encryptor)
}

class ETest(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<ETest>(TestTable)

  var varchar: String by TestTable.varchar
  var binary: ByteArray by TestTable.binary
}

// 사용은 투명합니다
val entity = ETest.new {
  varchar = "my secret value"
  binary = "another secret".toByteArray()
}

println(entity.varchar) // "my secret value" 출력
```

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :01-exposed-r2dbc-crypt:test

# 특정 테스트 클래스 실행
./gradlew :01-exposed-r2dbc-crypt:test --tests "exposed.examples.crypt.Ex01_EncryptedColumn"
```

## 참고 자료

- [Exposed Crypt 모듈](https://debop.notion.site/Exposed-Crypt-1c32744526b0802da419d5ce74d2c5f3)
