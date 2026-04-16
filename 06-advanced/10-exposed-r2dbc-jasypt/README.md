> 한국어 버전: [README.ko.md](README.ko.md)

# 10 Exposed R2DBC Jasypt (Deterministic Encryption)

This module covers how to integrate the **Jasypt (Java Simplified Encryption)** library with Exposed to provide transparent column encryption and decryption. The key feature of this integration is its **deterministic** nature: the same plaintext input always produces the same ciphertext output. This property allows encrypted data to be used directly in `WHERE` clause equality checks.

This module addresses the limitation of non-deterministic encryption approaches (such as the default `exposed-crypt` module) where encrypted data cannot be queried directly.

## Execution Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant Col as JasyptColumn
    participant Enc as Jasypt Encryptor
    participant DB as Database

    Note over App,DB: INSERT — Deterministic encryption (DeterministicAES / DeterministicRC4)
    App ->> Col: insert { it[name] = "Hong Gildong" }
    Col ->> Enc: encrypt("Hong Gildong")
    Enc -->> Col: "UPq8X_QFkR-tsUFSOwffVQ=="
    Col ->> DB: INSERT 'UPq8X_QFkR-tsUFSOwffVQ=='

    Note over App,DB: INSERT — Non-deterministic encryption (TripleDES / RC2)
    App ->> Col: insert { it[address] = "Seoul, Gangnam-gu" }
    Col ->> Enc: encrypt("Seoul, Gangnam-gu")
    Enc -->> Col: "<different ciphertext each time>"
    Col ->> DB: INSERT '<different ciphertext each time>'

    Note over App,DB: SELECT
    DB -->> Col: "UPq8X_QFkR-tsUFSOwffVQ=="
    Col ->> Enc: decrypt("UPq8X_QFkR-tsUFSOwffVQ==")
    Enc -->> Col: "Hong Gildong"
    Col -->> App: "Hong Gildong"

    Note over App,DB: WHERE search — only possible with deterministic encryption
    App ->> Col: where { name eq "Hong Gildong" }
    Col ->> Enc: encrypt("Hong Gildong")
    Enc -->> Col: "UPq8X_QFkR-tsUFSOwffVQ=="
    Col ->> DB: WHERE name = 'UPq8X_QFkR-tsUFSOwffVQ=='
    DB -->> App: matching rows returned
```

## Structure Diagram

```mermaid
%%{init: {"theme": "neutral"}}%%
classDiagram
    class IColumnType~T~ {
        <<interface>>
        +valueFromDB(value: Any): T
        +notNullValueToDB(value: T): Any
        +sqlType(): String
    }
    class JasyptVarCharColumn {
        <<bluetape4k-exposed>>
        +sqlType(): String
        +valueFromDB(value): String
        +notNullValueToDB(value): String
        -encryptor: StringEncryptor
    }
    class JasyptBinaryColumn {
        <<bluetape4k-exposed>>
        +sqlType(): String
        +valueFromDB(value): ByteArray
        +notNullValueToDB(value): ByteArray
        -encryptor: ByteEncryptor
    }
    class StringEncryptor {
        <<Jasypt Interface>>
        +encrypt(message: String): String
        +decrypt(encryptedMessage: String): String
    }
    class ByteEncryptor {
        <<Jasypt Interface>>
        +encrypt(binary: ByteArray): ByteArray
        +decrypt(encryptedBinary: ByteArray): ByteArray
    }
    note for JasyptVarCharColumn "Deterministic encryption — searchable in WHERE clause"
    note for JasyptBinaryColumn "Deterministic encryption — searchable in WHERE clause"

    IColumnType <|.. JasyptVarCharColumn
    IColumnType <|.. JasyptBinaryColumn
    JasyptVarCharColumn --> StringEncryptor : delegates to
    JasyptBinaryColumn --> ByteEncryptor : delegates to

    style IColumnType fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    style JasyptVarCharColumn fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    style JasyptBinaryColumn fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    style StringEncryptor fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    style ByteEncryptor fill:#E0F2F1,stroke:#80CBC4,color:#00695C
```

## Learning Objectives

- Understand how to define Jasypt encryption columns (`jasyptVarChar`, `jasyptBinary`)
- Perform transparent encryption and decryption of string and binary data
- Leverage deterministic encryption to query encrypted columns directly in `WHERE` clauses
- Apply Jasypt encryption columns in both DSL and DAO programming styles

## Core Concepts

### Deterministic Encryption

Unlike many standard encryption schemes that add randomness (salting, IV) to produce different ciphertexts for the same plaintext, Jasypt can be configured to produce consistent ciphertexts. This allows SQL equality comparisons (`WHERE encrypted_column = 'encrypted_value'`) to work correctly.

**Trade-off**: While enabling searchability, deterministic encryption offers weaker cryptographic strength against attacks that exploit patterns in repeated data. It is suitable for scenarios where searchability is a strict requirement and the sensitivity of the data allows for this trade-off.

### Column Types

| Type                                        | Description                                              |
|---------------------------------------------|----------------------------------------------------------|
| `jasyptVarChar(name, length, encryptor)`    | Defines a column that encrypts `String` values using Jasypt |
| `jasyptBinary(name, length, encryptor)`     | Defines a column that encrypts `ByteArray` values using Jasypt |

### Encryptors

The `Encryptors` enum specifies the encryption algorithm and implicitly handles key management configuration for Jasypt:

| Encryptor                    | Type             | WHERE Searchable |
|------------------------------|------------------|-----------------|
| `Encryptors.DeterministicAES` | Deterministic    | Yes             |
| `Encryptors.DeterministicRC4` | Deterministic    | Yes             |
| `Encryptors.TripleDES`        | Non-deterministic | No             |
| `Encryptors.RC2`              | Non-deterministic | No             |

## Example Overview

### `JasyptColumnTypeTest.kt` (DSL style)

Demonstrates usage of Jasypt encryption columns within the Exposed DSL.

- **CRUD Operations**: How to `insert` and `update` encrypted `String` and `ByteArray` fields. Encryption/decryption is transparent.
- **Searchability**: Explicitly highlights that encrypted columns can be used in `WHERE` clauses for `eq` comparisons — the key advantage of deterministic encryption.

### `JasyptColumnTypeDaoTest.kt` (DAO style)

Similar to `JasyptColumnTypeTest.kt` but applies the concepts to the Exposed DAO API.

- **Entity mapping**: How `jasyptVarChar` and `jasyptBinary` columns map to entity properties
- **Seamless DAO usage**: CRUD operations on entities are transparent, and queries with encrypted properties work as expected

## Code Examples

### 1. Define a table with Jasypt encryption columns

```kotlin
import io.bluetape4k.exposed.core.jasypt.jasyptVarChar
import io.bluetape4k.exposed.core.jasypt.jasyptBinary
import io.bluetape4k.crypto.encrypt.Encryptors

object UserSecrets: IntIdTable("user_secrets") {
  val username = varchar("username", 255)

  // Encrypted string column — deterministic AES (searchable in WHERE clause)
  val name = jasyptVarChar("name", 255, Encryptors.DeterministicAES).nullable().index()

  // Encrypted string column — deterministic RC4 (searchable in WHERE clause)
  val city = jasyptVarChar("city", 255, Encryptors.DeterministicRC4).nullable().index()

  // Encrypted binary column — TripleDES (non-deterministic, not searchable)
  val address = jasyptBinary("address", 255, Encryptors.TripleDES).nullable()
}
```

### 2. Insert and query encrypted data (DSL)

```kotlin
// Insert an encrypted record
val insertedName = "John"
val insertedCity = "Seoul"
val insertedAddress = "123 Teheran-ro, Gangnam-gu, Seoul"

val id = UserSecrets.insertAndGetId {
    it[name] = insertedName
    it[city] = insertedCity
    it[address] = insertedAddress.toByteArray()
}

// Retrieve and verify (values are transparently decrypted)
val row = UserSecrets.selectAll().where { UserSecrets.id eq id }.single()
row[UserSecrets.name] shouldBeEqualTo insertedName
row[UserSecrets.city] shouldBeEqualTo insertedCity
row[UserSecrets.address]?.toUtf8String() shouldBeEqualTo insertedAddress

// Query by deterministically-encrypted column (DeterministicAES / DeterministicRC4 only)
UserSecrets.selectAll()
    .where { UserSecrets.name eq insertedName }
    .count() shouldBeEqualTo 1L

UserSecrets.selectAll()
    .where { UserSecrets.city eq insertedCity }
    .count() shouldBeEqualTo 1L
```

## Running the Tests

```bash
# Run all tests in this module
./gradlew :10-exposed-r2dbc-jasypt:test

# Run a specific test class
./gradlew :10-exposed-r2dbc-jasypt:test --tests "exposed.r2dbc.examples.jasypt.JasyptColumnTypeTest"
```

## References

- [Exposed Jasypt](https://debop.notion.site/Exposed-Jasypt-1c32744526b080f08ab2f3e21149e9d7)
- [Exposed Crypt](https://debop.notion.site/Exposed-Crypt-1c32744526b0802da419d5ce74d2c5f3)
