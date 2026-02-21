# 02 R2DBC Virtual Threads Basic (가상 스레드 기본)

Exposed R2DBC + Java 21 Virtual Threads 환경에서 비동기 데이터베이스 작업을 수행하는 방법을 학습합니다. 블로킹 코드 스타일을 유지하면서 고성능 비동기 처리를 구현합니다.

## 학습 목표

- Java 21 Virtual Threads와 Exposed R2DBC 통합
- 블로킹 스타일 코드로 비동기 처리 구현
- Virtual Threads 환경에서의 트랜잭션 관리
- 전통적 스레드 풀 vs Virtual Threads 성능 비교
- Virtual Threads 사용 시 주의사항

## 핵심 개념

### Virtual Threads란?

Java 21에서 도입된 Virtual Threads는 가볍고 효율적인 스레드로, 수백만 개의 동시 작업을 처리할 수 있습니다. 기존 블로킹 I/O 코드를 변경 없이 비동기처럼 효율적으로 실행할 수 있습니다.

### Exposed R2DBC with Virtual Threads

```kotlin
// Virtual Threads에서 실행
fun main() = runBlocking(Dispatchers.IO) {
    val database = R2dbcDatabase.connect(...)

    // 블로킹 스타일로 작성하지만 내부적으로는 비동기로 동작
    val users = transaction {
        Users.selectAll().toList()
    }
}
```

### 구조화된 동시성

```kotlin
@OptIn(DelicateCoroutinesApi::class)
fun processUsers() = runBlocking {
    val users = getUsers()  // Virtual Thread에서 실행
    
    users.map { user ->
        async(Dispatchers.IO) {
            processUser(user)  // 각각 별도의 Virtual Thread
        }
    }.awaitAll()
}
```

## 코드 예제

### Repository with Virtual Threads

```kotlin
class UserRepository(private val database: R2dbcDatabase) {
    
    suspend fun findById(id: Long): User? = withContext(Dispatchers.IO) {
        transaction(database) {
            Users.selectAll()
                .where { Users.id eq id }
                .singleOrNull()
                ?.toUser()
        }
    }
    
    suspend fun save(user: User): Long = withContext(Dispatchers.IO) {
        transaction(database) {
            Users.insertAndGetId {
                it[name] = user.name
                it[email] = user.email
            }.value
        }
    }
    
    suspend fun findAll(): List<User> = withContext(Dispatchers.IO) {
        transaction(database) {
            Users.selectAll().map { it.toUser() }
        }
    }
}
```

## 성능 비교

| 특성        | 플랫폼 스레드  | Virtual Threads |
|-----------|----------|-----------------|
| 생성 비용     | 높음       | 매우 낮음           |
| 메모리 사용    | ~1MB/스레드 | ~1KB/스레드        |
| 컨텍스트 스위칭  | 비용 높음    | 비용 낮음           |
| 최대 동시 스레드 | 수천 개     | 수백만 개           |
| 블로킹 I/O   | 스레드 낭비   | 효율적 처리          |

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :08-r2dbc-coroutines:02-exposed-r2dbc-virtualthreads-basic:test
```

## 참고 자료

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Virtual Threads 가이드](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
