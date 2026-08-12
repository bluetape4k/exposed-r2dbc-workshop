> English version: [README.md](README.md)

# 07-spring-suspended-cache

Spring WebFlux + Exposed R2DBC 환경에서 Lettuce 기반의 Suspended Cache를 Coroutines로 구현하는 예제입니다. 동일한
`CountryR2dbcRepository` 인터페이스를 DB 직접 조회(Default)와 Redis 캐시 적용(Cached) 두 가지 방식으로 구현하여 캐시 유무에 따른 성능 차이를 비교할 수 있습니다.

## 문서

* [Exposed with Spring Suspended Cache](https://debop.notion.site/Exposed-with-Suspended-Spring-Cache-1db2744526b080769d2ef307e4a3c6c9)

## 기술 스택

| 구분        | 기술                             |
|-----------|--------------------------------|
| Framework | Spring Boot (WebFlux)          |
| ORM       | Exposed R2DBC                  |
| 비동기       | Kotlin Coroutines              |
| Cache     | Lettuce (Redis Coroutines API) |
| Codec     | FastFory, Fory, Kryo            |
| 압축        | LZ4, Snappy, Zstd              |
| DB        | H2 (기본), MySQL 8, PostgreSQL   |
| 컨테이너      | Testcontainers (DB + Redis)    |
| 서버        | Netty (Reactive)               |

## 실행 흐름

![07 spring suspended cache Sequence Flow diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-sequence-01.png)

## Cache 클래스 구조 (classDiagram)

![Cache (classDiagram) diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-class-02.png)

## Cache-Aside 패턴 흐름

![Cache-Aside diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-03.png)

## 프로젝트 구조

```
src/main/kotlin/exposed/r2dbc/examples/suspendedcache/
├── SpringSuspendedCacheApplication.kt           # Spring Boot 애플리케이션 진입점
├── cache/
│   ├── LettuceSuspendedCache.kt                 # Lettuce Coroutines 기반 캐시 구현
│   └── LettuceSuspendedCacheManager.kt          # 캐시 인스턴스 관리 매니저
├── config/
│   ├── ExposedR2dbcConfig.kt                    # R2DBC Database 및 ConnectionPool 설정
│   ├── LettuceCacheConfig.kt                    # Redis 클라이언트 및 CacheManager 설정
│   ├── NettyConfig.kt                           # Netty 서버 튜닝
│   └── R2dbcRepositoryConfig.kt                 # Repository Bean 등록 (Default/Cached)
├── controller/
│   ├── DefaultCountryController.kt              # DB 직접 조회 API (/default/countries)
│   └── CachedCountryController.kt               # Redis 캐시 적용 API (/cached/countries)
├── domain/
│   ├── model/
│   │   └── CountrySchema.kt                     # CountryTable 정의 + CountryRecord DTO + Mapper
│   └── repository/
│       ├── CountryR2dbcRepository.kt            # Repository 인터페이스
│       ├── DefaultCountryR2dbcRepository.kt     # DB 직접 조회 구현
│       └── CachedCountryR2dbcRepository.kt      # Redis 캐시 + DB 조회 (Decorator 패턴)
└── utils/
    └── DataPopulator.kt                         # 애플리케이션 시작 시 249개 국가 코드 샘플 데이터 삽입 (runBlocking 브릿지 패턴)
```

## Spring + Coroutine 브릿지 패턴 (`DataPopulator`)

`ApplicationListener<ApplicationReadyEvent>`의 `onApplicationEvent`는 일반(non-suspend) 함수입니다.
Exposed R2DBC의 `suspendTransaction`을 사용하려면 `runBlocking`으로 코루틴 세계를 브릿지해야 합니다.

```kotlin
@Component
class DataPopulator: ApplicationListener<ApplicationReadyEvent> {

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        // runBlocking: 현재 스레드를 블로킹하고 코루틴을 실행 (초기화 전용 패턴)
        // Dispatchers.IO: 249개 국가 코드 데이터 삽입에 최적화된 I/O 스레드 풀 사용
        runBlocking(Dispatchers.IO) {
            suspendTransaction {
                createTables()
                populateCountries()
            }
        }
    }
}
```

> **주의**: `runBlocking`은 초기화 로직에서만 사용합니다. Repository/Service 레이어에서는
> `suspend fun`과 `suspendTransaction`을 직접 사용해야 합니다.

## 아키텍처

### Decorator 패턴을 활용한 캐시 계층 분리

캐시 로직을 Repository 구현에서 분리하여 Decorator 패턴으로 적용합니다.
`CachedCountryR2dbcRepository`는 `DefaultCountryR2dbcRepository`를 래핑하여 Redis 캐시를 투명하게 적용합니다.

![Cache Decorator Layering diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-04.png)

### Bean 등록 구조

```kotlin
@Configuration
class R2dbcRepositoryConfig(private val suspendedCacheManager: LettuceSuspendedCacheManager) {

    @Bean(name = ["countryR2dbcRepository", "defaultCountryR2dbcRepository"])
    fun countryR2dbcRepository(): CountryR2dbcRepository =
        DefaultCountryR2dbcRepository()

    @Bean(name = ["cachedCountryR2dbcRepository"])
    fun cachedCountryR2dbcRepository(): CountryR2dbcRepository =
        CachedCountryR2dbcRepository(DefaultCountryR2dbcRepository(), cacheManager = suspendedCacheManager)
}
```

Controller에서 `@Qualifier`로 원하는 Repository를 선택합니다.

## 데이터베이스 스키마

### CountryTable

```kotlin
object CountryTable: IntIdTable("countries") {
    val code = varchar("code", 2).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description", eagerLoading = true).nullable()
}
```

- 249개 ISO 국가 코드를 샘플 데이터로 삽입
- `description`에 대용량 텍스트를 포함하여 캐시 효과를 체감할 수 있도록 구성

## 핵심 구현

### 1. LettuceSuspendedCache

Lettuce의 `RedisCoroutinesCommands`를 사용하여 `suspend` 함수로 Redis 캐시를 조작합니다. TTL 기반 자동 만료를 지원합니다.

```kotlin
class LettuceSuspendedCache<K: Any, V: Any>(
    val name: String,
    val commands: RedisCoroutinesCommands<String, V>,
    private val keyCommands: RedisCoroutinesCommands<String, String>,
    private val ttlSeconds: Long? = null,
) {
    private val indexKey: String = "$name:__keys"

    suspend fun get(key: K): V? = commands.get(keyStr(key))

    suspend fun put(key: K, value: V) {
        val redisKey = keyStr(key)
        if (ttlSeconds != null) commands.setex(redisKey, ttlSeconds, value)
        else commands.set(redisKey, value)
        keyCommands.sadd(indexKey, redisKey)
    }

    suspend fun evict(key: K) {
        val redisKey = keyStr(key)
        commands.del(redisKey)
        keyCommands.srem(indexKey, redisKey)
    }

    suspend fun clear() {
        val keys = keyCommands.smembers(indexKey)
            .filterNotNull()
            .toList()
        keys.chunked(100).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                keyCommands.unlink(*chunk.toTypedArray())
            }
        }
        keyCommands.unlink(indexKey)
    }
}
```

### 2. LettuceSuspendedCacheManager

캐시 인스턴스를 이름(name)별로 `getOrCreate()`로 관리하며, 휘발성 캐시 엔트리에 `LettuceBinaryCodec`(LZ4 + FastFory 직렬화)을 적용합니다.

```kotlin
@Bean
fun lettuceSuspendedCacheManager(redisClient: RedisClient): LettuceSuspendedCacheManager {
    return LettuceSuspendedCacheManager(
        redisClient = redisClient,
        ttlSeconds = 60L,
        codec = LettuceBinaryCodecs.lz4FastFory(),   // LZ4 압축 + FastFory (SCHEMA_CONSISTENT)
    )
}

// 캐시 인식 Repository 내부에서의 사용:
private val cache: LettuceSuspendedCache<String, CountryRecord> by lazy {
    cacheManager.getOrCreate(
        name = CACHE_NAME,
        ttlSeconds = 60,
    )
}
```

> **FastFory 캐시 계약**: 이 예제는 휘발성 Redis 캐시 엔트리에 `LettuceBinaryCodecs.lz4FastFory()`를 사용합니다. `CompatibleMode.SCHEMA_CONSISTENT` 모드이므로 Fory codec과 와이어 포맷이 호환되지 않으며 자동 fallback도 없습니다. codec을 변경하면 기존 캐시 엔트리를 비우거나 다시 생성해야 하며, FastFory를 영속 바이너리 컬럼이나 기타 내구성 데이터에 사용하지 마십시오.

### 3. CachedCountryR2dbcRepository

Cache-Aside 패턴을 구현합니다:

- **Read**: 캐시 조회 -> miss 시 DB 조회 후 캐시 저장
- **Update**: 캐시 무효화 후 DB 업데이트
- **Evict All**: 캐시 namespace index에 기록된 키를 배치 삭제

```kotlin
class CachedCountryR2dbcRepository(
    private val delegate: CountryR2dbcRepository,
    private val cacheManager: LettuceSuspendedCacheManager,
): CountryR2dbcRepository {

    override suspend fun findByCode(code: String): CountryRecord? {
        return cache.get(code)
            ?: delegate.findByCode(code)?.apply { cache.put(code, this) }
    }

    override suspend fun update(countryRecord: CountryRecord): Int {
        cache.evict(countryRecord.code)
        return delegate.update(countryRecord)
    }
}
```

## API 엔드포인트

### Default (DB 직접 조회)

| Method | Path                        | 설명                 |
|--------|-----------------------------|--------------------|
| GET    | `/default/countries/{code}` | 국가 코드로 조회 (캐시 미적용) |

### Cached (Redis 캐시 적용)

| Method | Path                       | 설명                      |
|--------|----------------------------|-------------------------|
| GET    | `/cached/countries/{code}` | 국가 코드로 조회 (Redis 캐시 적용) |

동일한 API 구조로 `/default`와 `/cached` 경로를 비교하여 캐시 적용 효과를 확인할 수 있습니다.

## 실행 방법

```bash
# 기본 실행 (H2 + Redis via Testcontainers)
./gradlew :07-spring-suspended-cache:bootRun

# PostgreSQL 사용
./gradlew :07-spring-suspended-cache:bootRun --args='--spring.profiles.active=postgres'

# MySQL 사용
./gradlew :07-spring-suspended-cache:bootRun --args='--spring.profiles.active=mysql'
```

Redis는 Testcontainers를 통해 자동으로 실행됩니다.

## 테스트

```bash
./gradlew :07-spring-suspended-cache:test
```

### 테스트 목록

- **DefaultCountryR2dbcRepositoryTest** - DB 직접 조회 Repository 테스트 (반복 로드, 업데이트)
- **CachedCountryR2dbcRepositoryTest** - 캐시 적용 Repository 테스트 (반복 로드, 업데이트, 캐시 전체 삭제)
- **DefaultCountryControllerTest** - DB 직접 조회 API 테스트 (순차/병렬 조회)
- **CachedCountryControllerTest** - 캐시 적용 API 테스트 (순차/병렬 조회)
- **ExposedR2dbcConfigTest** - R2DBC 설정 로드 검증
- **LettuceCacheConfigTest** - Redis 캐시 설정 로드 검증
- **R2dbcRepositoryConfigTest** - Repository Bean 등록 검증

테스트는 `@RepeatedTest`로 반복 실행하여 첫 번째(cold) 조회와 이후(warm/cached) 조회의 성능 차이를 확인합니다.

## Suspended Cache 작동 원리 (상세)

### LettuceSuspendedCache 내부 흐름

![Execution Flow diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-sequence-01.png)

### Index 기반 전체 캐시 삭제

Redis의 `KEYS` 또는 광범위한 keyspace scan은 대규모 데이터셋에서 Redis 서버에 부담을 줄 수 있습니다.
`LettuceSuspendedCache.clear()`는 전체 Redis keyspace를 훑지 않고, namespace가 직접 관리하는 `indexKey`를 `SMEMBERS`로 읽은 뒤 캐시 엔트리를 `UNLINK`로 배치 삭제하고 마지막에 index key를 삭제합니다:

![Indexed Cache Eviction diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-05.png)

`UNLINK`는 `DEL`과 달리 백그라운드에서 메모리를 해제하므로 Redis 이벤트 루프를 차단하지 않습니다.

### 직렬화/압축 전략

| 설정                  | Codec              | 압축  | 특징                           |
|---------------------|--------------------|-----|------------------------------|
| `lz4FastFory()`     | FastFory           | LZ4 | 휘발성 캐시용 (`SCHEMA_CONSISTENT`; Fory와 와이어 포맷 비호환) |
| `lz4Fory()`         | Fory (Java)        | LZ4 | 기존 Fory payload와의 호환 모드 |
| `lz4Kryo()`         | Kryo               | LZ4 | Fory보다 약간 느리지만 더 광범위한 타입 지원  |
| `snappyFory()`      | Fory               | Snappy | Google 압축 (네트워크 전송 최적화)   |
| `zstdFory()`        | Fory               | Zstd | 높은 압축률 (저장 공간 절약)            |

`CountryRecord`의 `description` 필드에 대용량 텍스트가 포함되므로 압축 효과가 큽니다.

## Further Reading

- [Exposed with Spring Suspended Cache](https://debop.notion.site/Exposed-with-Suspended-Spring-Cache-1db2744526b080769d2ef307e4a3c6c9)
- [Spring Caching Abstraction](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html)
