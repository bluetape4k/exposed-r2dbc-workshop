# 02 R2DBC Virtual Threads Basic (가상 스레드 기본)

Exposed R2DBC + Java 21 Virtual Threads 환경에서 비동기 데이터베이스 작업을 수행하는 방법을 학습합니다.
`runSuspendVT`, `virtualThreadTransaction`, `inTopLevelSuspendTransaction` 등 Virtual Threads 전용 API를 통해
블로킹 스타일 코드로 고성능 비동기 처리를 구현합니다.

> **요구 사항**: JDK 21 이상 (`@EnabledOnJre(JRE.JAVA_21)` 조건 적용)

---

## 학습 목표

- Java 21 Virtual Threads와 Exposed R2DBC 통합 방법 이해
- `runSuspendVT` / `virtualThreadTransaction` / `inTopLevelSuspendTransaction` API 활용
- `Dispatchers.newVT` 디스패처로 Virtual Threads 기반 병렬 처리 구현
- 기존 `suspendTransaction` 대비 Virtual Threads 트랜잭션의 차이점 파악
- MariaDB 계열 중첩 트랜잭션 제한 사항 파악

---

## 핵심 API

| API | 설명 |
|-----|------|
| `runSuspendVT { }` | Virtual Thread 기반 코루틴 테스트 실행기 (JUnit 5 전용) |
| `virtualThreadTransaction { }` | 현재 트랜잭션 내에서 Virtual Thread로 새 트랜잭션 생성·실행 |
| `inTopLevelSuspendTransaction { }` | 독립적인 최상위 suspend 트랜잭션 (기존 트랜잭션과 무관하게 새 트랜잭션 시작) |
| `Dispatchers.newVT` | Virtual Thread 기반 코루틴 디스패처 (`CoroutineScope(Dispatchers.newVT)`) |
| `suspendTransaction { }` | 일반 suspend 트랜잭션 (비교 기준) |

---

## 코드 예제

### 1. 기본 Virtual Thread 트랜잭션

`runSuspendVT`로 테스트를 실행하고, `virtualThreadTransaction`으로 중첩 트랜잭션을 생성합니다.

```kotlin
@EnabledOnJre(JRE.JAVA_21)
class Ex01_VirtualThreads: AbstractR2dbcExposedTest() {

    object VTester: IntIdTable("virtualthreads_table") {
        val name = varchar("name", 50).nullable()
    }

    // 현재 트랜잭션 내에서 Virtual Thread 기반 새 트랜잭션으로 조회
    suspend fun R2dbcTransaction.getTesterById(id: Int): ResultRow? =
        virtualThreadTransaction {
            VTester.selectAll()
                .where { VTester.id eq id }
                .singleOrNull()
        }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `virtual threads 를 이용하여 순차 작업 수행하기`(testDB: TestDB) = runSuspendVT {
        withTables(testDB, VTester) {
            val id = VTester.insertAndGetId { }
            getTesterById(id.value)!![VTester.id].value shouldBeEqualTo id.value
        }
    }
}
```

### 2. 중첩 트랜잭션 비동기 실행

`coroutineScope` + `async`로 병렬 INSERT를 수행한 뒤, `inTopLevelSuspendTransaction`으로 독립 트랜잭션에서 조회합니다.

```kotlin
@ParameterizedTest
@MethodSource(ENABLE_DIALECTS_METHOD)
fun `중첩된 virtual thread 용 트랜잭션을 async로 실행`(testDB: TestDB) = runSuspendVT {
    // MariaDB 계열은 중첩 트랜잭션 미지원 → 스킵
    Assumptions.assumeTrue { testDB !in TestDB.ALL_MARIADB_LIKE }

    withTables(testDB, VTester) {
        val recordCount = 5

        // 병렬 INSERT (suspendTransaction)
        List(recordCount) { index ->
            coroutineScope {
                async {
                    suspendTransaction {
                        maxAttempts = 10
                        VTester.insert { }
                    }
                }
            }
        }.awaitAll()

        // 병렬 SELECT (inTopLevelSuspendTransaction)
        val rows = List(recordCount) { index ->
            coroutineScope {
                async {
                    inTopLevelSuspendTransaction {
                        maxAttempts = 10
                        VTester.selectAll().map { it.toVRecord() }.toList()
                    }
                }
            }
        }.awaitAll().flatten()

        rows shouldHaveSize recordCount * recordCount
    }
}
```

### 3. `Dispatchers.newVT` 기반 병렬 처리

`CoroutineScope(Dispatchers.newVT)`로 Virtual Thread 디스패처를 생성하고, `launch`로 병렬 INSERT를 수행합니다.

```kotlin
@ParameterizedTest
@MethodSource(ENABLE_DIALECTS_METHOD)
fun `다수의 비동기 작업을 수행 후 대기`(testDB: TestDB) = runSuspendVT {
    withTables(testDB, VTester) {
        val recordCount = 10
        val results = CopyOnWriteArrayList<Int>()

        val vtScope = CoroutineScope(Dispatchers.newVT)
        List(recordCount) { index ->
            vtScope.launch {
                inTopLevelSuspendTransaction(
                    transactionIsolation = db.transactionManager.defaultIsolationLevel!!,
                    db = db
                ) {
                    maxAttempts = 5
                    VTester.insert { }
                    results.add(index + 1)
                }
            }
        }.joinAll()

        results.count() shouldBeEqualTo recordCount
        VTester.selectAll().count() shouldBeEqualTo recordCount.toLong()
    }
}
```

### 4. 조건부 조회

일반 `selectAll().where { }` 조회를 Virtual Thread 환경에서 실행합니다.

```kotlin
@ParameterizedTest
@MethodSource(ENABLE_DIALECTS_METHOD)
fun `virtual threads 환경에서 조건 조회`(testDB: TestDB) = runSuspendVT {
    withTables(testDB, VTester) {
        listOf("alpha", "beta", "gamma").forEach { name ->
            VTester.insert { it[VTester.name] = name }
        }

        val row = VTester.selectAll()
            .where { VTester.name eq "beta" }
            .singleOrNull()

        row?.getOrNull(VTester.name) shouldBeEqualTo "beta"
    }
}
```

---

## Virtual Thread (JDK 21) 활용 이점

JDK 21의 Virtual Threads(Project Loom)는 기존 플랫폼 스레드의 한계를 극복합니다.

### 성능 비교

| 특성             | 플랫폼 스레드         | Virtual Threads      |
|------------------|----------------------|----------------------|
| 생성 비용         | 높음 (~1ms)          | 매우 낮음 (~수 마이크로초) |
| 메모리 사용       | ~1MB/스레드          | ~수 KB/스레드         |
| 컨텍스트 스위칭   | OS 수준, 비용 높음   | JVM 수준, 비용 낮음   |
| 최대 동시 스레드  | 수천 개              | 수백만 개             |
| 블로킹 I/O       | 스레드 점유          | 자동 언마운트·재마운트  |
| JDK 요구사항     | 모든 버전             | 21+                  |

### R2DBC + Virtual Threads 조합의 이점

```
기존 플랫폼 스레드 모델:
┌─────────────────────────────────────────────────┐
│ Thread Pool (수백 개 한계)                       │
│  [Thread-1] → SQL wait → [Thread-1 block]       │
│  [Thread-2] → SQL wait → [Thread-2 block]       │
│  ...         (I/O 대기 중 스레드 낭비)           │
└─────────────────────────────────────────────────┘

Virtual Threads 모델 (JDK 21+):
┌─────────────────────────────────────────────────┐
│ Carrier Thread Pool (CPU 코어 수)                │
│  [Carrier-1] ← 마운트 → [VThread-1] SQL 실행    │
│               ← SQL wait 발생                    │
│  [Carrier-1] ← 마운트 → [VThread-2] 다른 작업   │
│               (VThread-1은 suspend, 스레드 해제) │
│  ...         (수백만 VThread 동시 처리 가능)     │
└─────────────────────────────────────────────────┘
```

### Coroutine Scope + Virtual Threads 관리 다이어그램

```
runSuspendVT { }                         ← Virtual Thread 기반 코루틴 테스트 실행기
│
├── withTables(testDB, VTester)          ← 테이블 생성, 트랜잭션 컨텍스트 시작
│   │
│   ├── virtualThreadTransaction { }     ← 현재 트랜잭션에서 새 VT 트랜잭션 생성
│   │   └── VTester.selectAll()          ← R2DBC 비동기 SQL (VT에서 실행)
│   │
│   ├── CoroutineScope(Dispatchers.newVT)
│   │   ├── launch { inTopLevelSuspendTransaction { VTester.insert { } } }
│   │   ├── launch { inTopLevelSuspendTransaction { VTester.insert { } } }
│   │   └── ... (수백만 개 동시 실행 가능)
│   │
│   └── joinAll(...)                     ← 모든 VT 작업 완료 대기
│
└── 테이블 자동 정리 (DROP)

핵심 API 역할:
  Dispatchers.newVT        → Virtual Thread 기반 코루틴 디스패처
  virtualThreadTransaction → 현재 트랜잭션 컨텍스트에서 VT로 분기
  inTopLevelSuspendTransaction → 독립 커넥션·독립 트랜잭션 (병렬 I/O에 적합)
  runSuspendVT             → JUnit 5 테스트를 VT 코루틴으로 실행
```

### 언제 Virtual Threads를 선택해야 하나?

- **I/O 집약적 작업**: DB 쿼리, 외부 API 호출 등 대기 시간이 긴 작업이 많을 때
- **높은 동시성 요구**: 수천~수백만 개의 동시 요청을 처리해야 할 때
- **기존 블로킹 코드 활용**: 레거시 JDBC 라이브러리 등 블로킹 API를 그대로 사용해야 할 때
- **Kotlin 코루틴과 병행**: `Dispatchers.newVT`로 기존 코루틴 코드에 자연스럽게 통합

---

## 주의 사항

- **JDK 21 필수**: 테스트 클래스에 `@EnabledOnJre(JRE.JAVA_21)` 적용되어 있어, JDK 21 미만에서는 자동 스킵됩니다.
- **MariaDB 계열 중첩 트랜잭션 미지원**: `Assumptions.assumeTrue { testDB !in TestDB.ALL_MARIADB_LIKE }` 조건으로 MariaDB에서는 중첩 트랜잭션 테스트를 스킵합니다.
- **CopyOnWriteArrayList 사용**: 여러 Virtual Thread에서 동시에 결과를 수집할 때 스레드 안전한 컬렉션을 사용해야 합니다.
- **maxAttempts 설정**: 병렬 트랜잭션에서 충돌이 발생할 수 있으므로 `maxAttempts = 5~10` 재시도 설정을 권장합니다.

---

## 테스트 실행

```bash
# 이 모듈의 모든 테스트 실행
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test

# H2만 사용하는 빠른 테스트
./gradlew :02-exposed-r2dbc-virtualthreads-basic:test -PuseFastDB=true
```

---

## 참고 자료

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Virtual Threads — Java 21 Guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Kotlin Coroutines + Virtual Threads](https://kotlinlang.org/docs/coroutines-overview.html)
