> 한국어 버전: [README.ko.md](README.ko.md)

# 01 R2DBC Coroutines Basic

Learn how to perform asynchronous database operations in an Exposed R2DBC + Kotlin Coroutines environment. Write readable asynchronous code by wrapping the R2DBC async API with coroutines.

## Learning Objectives

- Perform async transactions with `suspendTransaction`
- Execute parallel transactions with `inTopLevelSuspendTransaction` + `async`
- Stream reactive results using Flow
- Integrate Coroutine Dispatcher with Exposed R2DBC
- Handle exceptions within a coroutine context

## Tech Stack

| Category  | Technology                                              |
|-----------|---------------------------------------------------------|
| ORM       | Exposed R2DBC DSL                                       |
| Async     | Kotlin Coroutines + Flow                                |
| DB        | H2 (default), MariaDB, MySQL 8, PostgreSQL              |
| Container | Testcontainers                                          |
| Test      | JUnit 5 + bluetape4k-assertions + ParameterizedTest (multi-DB support) |

## Execution Flow

### Coroutine + R2DBC Transaction

![Coroutine + R2DBC Transaction diagram](../../docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-sequence-01.png)

### Flow Collection Patterns

![Flow Collection Patterns diagram](../../docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-02.png)

## Coroutine State Diagram

![Coroutine State Diagram diagram](../../docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-03.png)

## Key Concepts

### `suspendTransaction` vs `transaction`

| Property              | `transaction { }`                       | `suspendTransaction { }`              |
|-----------------------|-----------------------------------------|---------------------------------------|
| Execution mode        | Synchronous (blocking)                  | Asynchronous (coroutine suspend)      |
| Thread occupancy      | Thread held throughout transaction      | Thread released on suspend            |
| Usage location        | Regular function (`fun`)               | suspend function or inside coroutine  |
| R2DBC compatibility   | Not compatible (R2DBC is async-only)    | Compatible                            |
| Nested support        | `nestedTransactionMode` setting         | Use `inTopLevelSuspendTransaction`    |
| Spring integration    | Corresponds to `@Transactional`         | `@Transactional` + coroutine support  |

```kotlin
// Synchronous (JDBC only) - cannot be used with R2DBC
transaction {
    MyTable.selectAll().toList()   // blocking call
}

// Asynchronous (R2DBC) - for use in coroutine context
suspend fun query() = suspendTransaction {
    MyTable.selectAll().toList()   // suspend / non-blocking
}
```

### Coroutine Scope Management Diagram

![Coroutine Scope Management diagram](../../docs/images/readme-diagrams/08-r2dbc-coroutines-01-exposed-r2dbc-coroutines-basic-architecture-04.png)

**Key Rules:**
- `suspendTransaction`: reuses the DB connection from the current coroutine context (nestable)
- `inTopLevelSuspendTransaction`: always creates a new connection and transaction (suited for independent execution)
- `withContext(dispatcher)`: run transaction on a different thread pool by switching dispatcher

### suspendTransaction

A `suspend` function that performs a transaction in a coroutine context.

```kotlin
suspend fun getUsers(): List<UserRecord> = suspendTransaction {
    Users.selectAll().map { it.toUserRecord() }
}
```

### Parallel Transactions with async

Executes multiple transactions in parallel using `inTopLevelSuspendTransaction` + `async`.

```kotlin
val ioScope = CoroutineScope(Dispatchers.IO)

val usersDeferred = ioScope.async {
    inTopLevelSuspendTransaction(db = db) {
        Users.selectAll().toList()
    }
}

val ordersDeferred = ioScope.async {
    inTopLevelSuspendTransaction(db = db) {
        Orders.selectAll().toList()
    }
}

val (users, orders) = awaitAll(usersDeferred, ordersDeferred)
```

### Flow Streaming

`selectAll()` returns a `Flow<ResultRow>` directly. Collect it inside a transaction context.

```kotlin
suspend fun streamUsers(): List<UserRecord> = suspendTransaction {
    Users.selectAll()
        .map { it.toUserRecord() }
        .toList()
}
```

## Code Examples

### Basic CRUD with Coroutines

```kotlin
suspend fun createUser(name: String, email: String): Long = suspendTransaction {
    Users.insertAndGetId {
        it[Users.name] = name
        it[Users.email] = email
    }.value
}

suspend fun findUserById(id: Long): UserRecord? = suspendTransaction {
    Users.selectAll()
        .where { Users.id eq id }
        .singleOrNull()
        ?.toUserRecord()
}

suspend fun updateUser(id: Long, name: String): Int = suspendTransaction {
    Users.update({ Users.id eq id }) {
        it[Users.name] = name
    }
}

suspend fun deleteUser(id: Long): Int = suspendTransaction {
    Users.deleteWhere { Users.id eq id }
}
```

### Parallel Transaction Execution

```kotlin
suspend fun parallelOperations(db: R2dbcDatabase) {
    val ioScope = CoroutineScope(Dispatchers.IO)

    val insertJob = ioScope.async {
        inTopLevelSuspendTransaction(db = db) {
            // INSERT operation
            Users.insert { it[name] = "User1" }
        }
    }

    val updateJob = ioScope.async {
        inTopLevelSuspendTransaction(db = db) {
            // UPDATE operation
            Users.update({ Users.id eq 1 }) { it[name] = "Updated" }
        }
    }

    val (insertResult, updateResult) = awaitAll(insertJob, updateJob)
}
```

### Exception Handling

```kotlin
suspend fun safeOperation(): Result<UserRecord> = runCatching {
    suspendTransaction {
        Users.selectAll()
            .where { Users.id eq 1 }
            .single()
            .toUserRecord()
    }
}.onFailure { e ->
    log.error(e) { "Transaction failed" }
}
```

## Example Test Structure

This module validates core coroutine transaction patterns against multiple DBs using `ParameterizedTest`.

- `Ex01_Coroutines`: Sequential/parallel transactions, nested transactions, async operation combinations
- `Ex02_CoroutinesFlow`: Flow-based result collection, parallel execution with `inTopLevelSuspendTransaction`

`Ex02_CoroutinesFlow` in particular demonstrates two practical patterns as minimal examples:

1. Safely collecting `selectAll()` results via Flow operations (`map`, `toList`)
2. Running independent transactions in parallel from multiple coroutines and verifying results consistently

## Running Tests

```bash
# Run all tests in this module
./gradlew :01-exposed-r2dbc-coroutines-basic:test

# Run a specific test
./gradlew :01-exposed-r2dbc-coroutines-basic:test --tests "exposed.r2dbc.examples.coroutines.Ex01_Coroutines"

# Run only the Flow example tests
./gradlew :01-exposed-r2dbc-coroutines-basic:test --tests "exposed.r2dbc.examples.coroutines.Ex02_CoroutinesFlow"
```

## References

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Exposed R2DBC](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
