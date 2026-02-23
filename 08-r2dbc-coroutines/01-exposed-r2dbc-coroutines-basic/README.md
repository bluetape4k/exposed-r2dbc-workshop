# 01 R2DBC Coroutines Basic (코루틴 기본)

Exposed R2DBC + Kotlin Coroutines 환경에서 비동기 데이터베이스 작업을 수행하는 방법을 학습합니다. R2DBC의 비동기 API를 코루틴으로 래핑하여 가독성 높은 비동기 코드를 작성합니다.

## 학습 목표

- `suspendTransaction`으로 비동기 트랜잭션 수행
- `suspendTransactionAsync`로 병렬 트랜잭션 실행
- Flow를 사용한 반응형 결과 스트리밍
- Coroutine Dispatcher와 Exposed R2DBC 통합
- 코루틴 컨텍스트 내에서의 예외 처리

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC DSL                               |
| 비동기  | Kotlin Coroutines + Flow                        |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 핵심 개념

### suspendTransaction

코루틴 환경에서 트랜잭션을 수행하는 `suspend` 함수입니다.

```kotlin
suspend fun getUsers(): List<UserRecord> = suspendTransaction {
    Users.selectAll().map { it.toUserRecord() }
}
```

### suspendTransactionAsync

여러 트랜잭션을 병렬로 실행합니다.

```kotlin
val usersDeferred = suspendTransactionAsync {
    Users.selectAll().toFastList()
}

val ordersDeferred = suspendTransactionAsync {
    Orders.selectAll().toFastList()
}

val (users, orders) = awaitAll(usersDeferred, ordersDeferred)
```

### Flow 스트리밍

대용량 결과를 효율적으로 처리하기 위해 Flow를 사용합니다.

```kotlin
fun streamUsers(): Flow<UserRecord> = Users
    .selectAll()
    .asFlow()
    .map { it.toUserRecord() }
```

## 코드 예제

### 기본 CRUD with Coroutines

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

### 병렬 트랜잭션 실행

```kotlin
suspend fun parallelOperations() = coroutineScope {
    val insertJob = suspendTransactionAsync {
        // INSERT 작업
        Users.insert { it[name] = "User1" }
    }
    
    val updateJob = suspendTransactionAsync {
        // UPDATE 작업
        Users.update({ Users.id eq 1 }) { it[name] = "Updated" }
    }
    
    val (insertResult, updateResult) = awaitAll(insertJob, updateJob)
}
```

### 예외 처리

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

## 예제 테스트 구성

이 모듈은 코루틴 트랜잭션의 핵심 패턴을 `ParameterizedTest` 기반으로 멀티 DB 환경에서 검증합니다.

- `Ex01_Coroutines`: 순차/병렬 트랜잭션, 중첩 트랜잭션, 비동기 작업 조합
- `Ex02_CoroutinesFlow`: Flow 기반 결과 수집, `inTopLevelSuspendTransaction` 병렬 실행

특히 `Ex02_CoroutinesFlow`는 다음 두 가지 실무 패턴을 최소 예제로 보여줍니다.

1. `selectAll()` 결과를 Flow 연산(`map`, `toList`)으로 안전하게 수집
2. 복수 코루틴에서 독립 트랜잭션을 병렬 실행한 뒤 결과를 일관성 있게 검증

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :01-exposed-r2dbc-coroutines-basic:test

# 특정 테스트만 실행
./gradlew :01-exposed-r2dbc-coroutines-basic:test --tests "exposed.r2dbc.examples.coroutines.Ex01_Coroutines"

# Flow 예제 테스트만 실행
./gradlew :01-exposed-r2dbc-coroutines-basic:test --tests "exposed.r2dbc.examples.coroutines.Ex02_CoroutinesFlow"
```

## 참고 자료

- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Exposed R2DBC](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
