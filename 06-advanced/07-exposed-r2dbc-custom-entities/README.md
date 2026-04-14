> 한국어 버전: [README.ko.md](README.ko.md)

# 07 Exposed R2DBC Custom Entities (ID Generation Strategies)

This module demonstrates a powerful pattern in Exposed: creating reusable base `Table` and `Entity` classes that encapsulate specific primary key strategies. Instead of manually defining the `id` column and its default generator for every table, you simply inherit from a pre-configured base class.

This approach builds on the concepts from the `06-custom-columns` module, packaging custom client-side default generators into convenient, reusable abstractions.

## Structure Diagram

```mermaid
%%{init: {"theme": "neutral"}}%%
classDiagram
    class IdTable~ID~ {
        <<abstract>>
        +id: Column~EntityID~ID~~
    }
    class SnowflakeIdTable {
        <<abstract>>
        +id: Column~EntityID~Long~~
        clientDefault: SnowflakeId
    }
    class KsuidTable {
        <<abstract>>
        +id: Column~EntityID~String~~
        clientDefault: KSUID-Base62 27chars
    }
    class KsuidMillisTable {
        <<abstract>>
        +id: Column~EntityID~String~~
        clientDefault: KSUID-Millis 27chars
    }
    class TimebasedUUIDTable {
        <<abstract>>
        +id: Column~EntityID~UUID~~
        clientDefault: UUIDv1 RFC4122
    }
    class TimebasedUUIDBase62Table {
        <<abstract>>
        +id: Column~EntityID~String~~
        clientDefault: UUIDv1+Base62 22chars
    }
    class T1["T1 : SnowflakeIdTable"] {
        +name: Column~String~
        +age: Column~Int~
    }

    IdTable <|-- SnowflakeIdTable
    IdTable <|-- KsuidTable
    IdTable <|-- KsuidMillisTable
    IdTable <|-- TimebasedUUIDTable
    IdTable <|-- TimebasedUUIDBase62Table
    SnowflakeIdTable <|-- T1

    style IdTable fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    style SnowflakeIdTable fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    style KsuidTable fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    style KsuidMillisTable fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    style TimebasedUUIDTable fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    style TimebasedUUIDBase62Table fill:#E0F2F1,stroke:#80CBC4,color:#00695C
    style T1 fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
```

```mermaid
%%{init: {"theme": "neutral"}}%%
erDiagram
    T_SNOWFLAKE {
        BIGINT id PK "Snowflake ID (auto-generated)"
        VARCHAR name "255"
        INT age
    }
    T_KSUID {
        VARCHAR id PK "KSUID Base62 (27 chars)"
        VARCHAR name "255"
        INT age
    }
    T_KSUID_MILLIS {
        VARCHAR id PK "KSUID Millis (27 chars)"
        VARCHAR name "255"
        INT age
    }
    T_TIMEBASED_UUID {
        UUID id PK "UUIDv1 (RFC 4122)"
        VARCHAR name "255"
        INT age
    }
    T_TIMEBASED_UUID_BASE62 {
        VARCHAR id PK "UUIDv1+Base62 (22 chars)"
        VARCHAR name "255"
        INT age
    }
```

## ID Generation Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant Table as CustomIdTable
    participant Gen as ID Generator
    participant DB as Database

    Note over App,DB: DAO — Product.new { ... }
    App ->> Table: Product.new { name = "Laptop" }
    Table ->> Gen: invoke clientDefault lambda
    Gen -->> Table: generatedId (e.g. 1234567890L)
    Table ->> DB: INSERT INTO products (id, name) VALUES (1234567890, 'Laptop')

    Note over App,DB: DSL — Products.insert { ... }
    App ->> Table: Products.insert { it[name] = "Mouse" }
    Table ->> Gen: invoke clientDefault lambda
    Gen -->> Table: generatedId
    Table ->> DB: INSERT INTO products (id, name) VALUES (generatedId, 'Mouse')
```

## ID Strategy Selection Flowchart

```mermaid
%%{init: {"theme": "neutral"}}%%
flowchart TD
    A[Choose ID Strategy] --> B{ID Type}
    B --> C[Numeric Long] --> D[SnowflakeIdTable\nmilli-sorted, 64bit]
    B --> E{String}
    E --> F{Need UUID standard?}
    F --> G[Yes] --> H{Compact representation?}
    H --> I[No] --> J[TimebasedUUIDTable\nUUID 36 chars, RFC 4122]
    H --> K[Yes] --> L[TimebasedUUIDBase62Table\nString 22 chars]
    F --> M[No] --> N{Millisecond precision?}
    N --> O[No] --> P[KsuidTable\nsecond precision, 27 chars]
    N --> Q[Yes] --> R[KsuidMillisTable\nmillis, 27 chars]

    classDef blue   fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    classDef green  fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    classDef purple fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    classDef orange fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    classDef teal   fill:#E0F2F1,stroke:#80CBC4,color:#00695C

    class A blue
    class B,E,F,H,N purple
    class D,G,I green
    class J,L purple
    class P,R orange
    class C,K,M,O,Q teal
```

## ID Generation Strategy Comparison

Different ID generation strategies vary in storage type, sortability, and length. Choose the strategy that best fits your use case.

| Base Class                    | ID Type          | Storage Type    | Sortable | Length | Generation Method | Primary Use Case                              |
|-------------------------------|------------------|-----------------|----------|--------|-------------------|-----------------------------------------------|
| `SnowflakeIdTable`            | `Long`           | `BIGINT`        | Millis   | 64bit  | Snowflake algorithm | High-performance numeric IDs for distributed systems |
| `KsuidTable`                  | `String`         | `VARCHAR(27)`   | Seconds  | 27ch   | KSUID (Base62)    | URL-friendly, sortable string IDs             |
| `KsuidMillisTable`            | `String`         | `VARCHAR(27)`   | Millis   | 27ch   | KSUID Millis      | KSUID with millisecond precision              |
| `TimebasedUUIDTable`          | `java.util.UUID` | `UUID`          | 100ns    | 36ch   | UUIDv1            | Systems requiring UUID standard, RFC 4122     |
| `TimebasedUUIDBase62Table`    | `String`         | `VARCHAR(22)`   | 100ns    | 22ch   | UUIDv1 + Base62   | Compact URL-safe representation of time-based UUID |

### Strategy Selection Guide

```
Need a numeric ID?
    YES → SnowflakeIdTable (Long, 64-bit, millis-sorted)

Need a string ID?
    Sortable + UUID standard compliance?
        YES → TimebasedUUIDTable (UUID, 36 chars, RFC 4122)
        Need compact representation?
            YES → TimebasedUUIDBase62Table (String, 22 chars)
    Lexicographic sort + URL-friendly?
        Second precision → KsuidTable (String, 27 chars)
        Millisecond precision → KsuidMillisTable (String, 27 chars)
```

## Learning Objectives

- Understand how to create custom base `IdTable` and `Entity` classes
- Learn how to abstract common ID generation strategies such as Snowflake, KSUID, and time-based UUID
- Simplify table definitions through custom base class inheritance
- Use these custom entities seamlessly in both DSL and DAO patterns

## Provided Examples

This module provides several ready-to-use base table/entity pairs for various ID generation needs.

### SnowflakeIdTable / SnowflakeIdEntity

| Item         | Description                                                   |
|--------------|---------------------------------------------------------------|
| **ID Type**  | `Long`                                                        |
| **Generator**| Generates k-ordered unique `Long` IDs using the Snowflake algorithm |
| **Use Case** | Ideal for distributed systems requiring roughly time-ordered unique numeric IDs |

### KsuidTable / KsuidEntity

| Item         | Description                                              |
|--------------|----------------------------------------------------------|
| **ID Type**  | `String` (varchar 27)                                    |
| **Generator**| Generates K-Sortable Unique Identifiers (KSUID)          |
| **Use Case** | Excellent when IDs need to be both unique and lexicographically sortable by creation time |

### KsuidMillisTable / KsuidMillisEntity

| Item         | Description                                     |
|--------------|-------------------------------------------------|
| **ID Type**  | `String` (varchar 27)                           |
| **Generator**| Generates KSUID with millisecond precision      |
| **Use Case** | Similar to KSUID but with finer time resolution |

### TimebasedUUIDTable / TimebasedUUIDEntity

| Item         | Description                                                |
|--------------|------------------------------------------------------------|
| **ID Type**  | `java.util.UUID`                                           |
| **Generator**| Generates time-based (version 1) UUIDs                    |
| **Use Case** | Useful when you need standard UUID format that is time-ordered |

### TimebasedUUIDBase62Table / TimebasedUUIDBase62Entity

| Item         | Description                                                                                  |
|--------------|----------------------------------------------------------------------------------------------|
| **ID Type**  | `String` (varchar 22)                                                                        |
| **Generator**| Generates time-based UUID and encodes it with Base62 for a shorter, URL-friendly string      |
| **Use Case** | When you need time-ordered UUIDs but want a more compact format than the standard 36-char string |

## How It Works

These base tables are typically implemented as `abstract class` inheriting from `IdTable`. The `id` column is overridden and configured with the desired type and `clientDefault` generator. Corresponding `Entity` and `EntityClass` are also provided to complete the abstraction.

## Code Example: Using SnowflakeIdTable

By inheriting from `SnowflakeIdTable`, you get the `id` column and auto-generation for free.

```kotlin
import io.bluetape4k.exposed.dao.id.SnowflakeIdTable
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntity
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntityClass
import io.bluetape4k.exposed.dao.id.SnowflakeIdEntityID

// 1. Define table by inheriting SnowflakeIdTable
object Products: SnowflakeIdTable("products") {
    val name = varchar("name", 255)
    val price = integer("price")
}

// 2. Define entity by inheriting SnowflakeIdEntity
class Product(id: SnowflakeIdEntityID): SnowflakeIdEntity(id) {
    companion object: SnowflakeIdEntityClass<Product>(Products)

    var name by Products.name
    var price by Products.price
}

// 3. Usage — ID is generated automatically
transaction {
    // DAO style
    val newProduct = Product.new {
        name = "Laptop"
        price = 1200
    }
    // ID already assigned: newProduct.id

    // DSL style
    Products.insert {
        it[name] = "Mouse"
        it[price] = 25
    }
    // Snowflake ID is auto-generated and inserted
}
```

This pattern significantly reduces boilerplate code and ensures a consistent primary key strategy across all tables that use it.

## Running the Tests

The tests in this module demonstrate record creation, batch insertion, and retrieval for each custom ID table type, in both standard and coroutine contexts.

```bash
# Run all tests in this module
./gradlew :07-exposed-r2dbc-custom-entities:test

# Run tests for a specific entity type (e.g. Snowflake)
./gradlew :07-exposed-r2dbc-custom-entities:test --tests "exposed.r2dbc.examples.custom.entities.SnowflakeIdTableTest"

# Run KSUID-based tests
./gradlew :07-exposed-r2dbc-custom-entities:test --tests "exposed.r2dbc.examples.custom.entities.KsuidTableTest"

# Run time-based UUID tests
./gradlew :07-exposed-r2dbc-custom-entities:test --tests "exposed.r2dbc.examples.custom.entities.TimebasedUUIDTableTest"
```

## References

- [Custom IdTable & Entities](https://debop.notion.site/Custom-Table-Entities-1c32744526b0804bad10ea3a0dce6c13)
