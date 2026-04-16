> 한국어 버전: [README.ko.md](README.ko.md)

# 07-spring-suspended-cache

An example implementing a Lettuce-based Suspended Cache with Coroutines in a Spring WebFlux + Exposed R2DBC environment. The same `CountryR2dbcRepository` interface is implemented in two ways — direct DB query (Default) and Redis cache (Cached) — to compare performance differences with and without caching.

## Documentation

* [Exposed with Spring Suspended Cache](https://debop.notion.site/Exposed-with-Suspended-Spring-Cache-1db2744526b080769d2ef307e4a3c6c9)

## Tech Stack

| Category    | Technology                             |
|-------------|----------------------------------------|
| Framework   | Spring Boot (WebFlux)                  |
| ORM         | Exposed R2DBC                          |
| Async       | Kotlin Coroutines                      |
| Cache       | Lettuce (Redis Coroutines API)         |
| Codec       | Fory, Kryo5                            |
| Compression | LZ4, Snappy, Zstd                      |
| DB          | H2 (default), MySQL 8, PostgreSQL      |
| Container   | Testcontainers (DB + Redis)            |
| Server      | Netty (Reactive)                       |

## Execution Flow

```mermaid
sequenceDiagram
    participant C as Controller/Service
    participant Cache as LettuceSuspendedCache (Redis)
    participant Repo as DefaultCountryR2dbcRepository
    participant DB as R2DBC Database

    C ->> Cache: cache.get(code)
    alt Cache HIT
        Cache -->> C: return cached result
    else Cache MISS
        Cache -->> C: null
        C ->> Repo: delegate.findByCode(code) (suspend)
        Repo ->> DB: suspendTransaction { SELECT }
        DB -->> Repo: ResultRow
        Repo -->> C: CountryRecord
        C ->> Cache: cache.put(code, result) (TTL 60s)
        Cache -->> C: return freshly fetched result
    end
```

## Cache Class Structure

```mermaid
%%{init: {"theme": "neutral"}}%%
classDiagram
    class CountryR2dbcRepository {
        <<interface>>
        +findByCode(code) CountryRecord?
        +update(record) Int
        +evictCacheAll()
    }
    class DefaultCountryR2dbcRepository {
        +findByCode(code) CountryRecord?
        +update(record) Int
        +evictCacheAll()
    }
    class CachedCountryR2dbcRepository {
        -delegate CountryR2dbcRepository
        -cacheManager LettuceSuspendedCacheManager
        -cache LettuceSuspendedCache
        +findByCode(code) CountryRecord?
        +update(record) Int
        +evictCacheAll()
    }
    class LettuceSuspendedCache~K,V~ {
        +name String
        +commands RedisCoroutinesCommands
        +ttlSeconds Long
        +get(key) V?
        +put(key, value)
        +evict(key)
        +clear()
    }
    class LettuceSuspendedCacheManager {
        -redisClient RedisClient
        -ttlSeconds Long
        -codec LettuceBinaryCodec
        +getOrCreate(name, ttlSeconds) LettuceSuspendedCache
    }

    CountryR2dbcRepository <|.. DefaultCountryR2dbcRepository
    CountryR2dbcRepository <|.. CachedCountryR2dbcRepository
    CachedCountryR2dbcRepository --> DefaultCountryR2dbcRepository : delegates
    CachedCountryR2dbcRepository --> LettuceSuspendedCacheManager : uses
    LettuceSuspendedCacheManager --> LettuceSuspendedCache : creates

    note for CachedCountryR2dbcRepository "Decorator pattern\nCache-Aside strategy"
    note for LettuceSuspendedCache "Lettuce Coroutines API\nsuspend fun based"

    style CountryR2dbcRepository fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    style DefaultCountryR2dbcRepository fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    style CachedCountryR2dbcRepository fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    style LettuceSuspendedCache fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    style LettuceSuspendedCacheManager fill:#E0F2F1,stroke:#80CBC4,color:#00695C
```

## Cache-Aside Pattern Flow

```mermaid
%%{init: {"theme": "neutral"}}%%
flowchart TD
    Request["Request: findByCode(code)"]
    CacheGet["Redis GET\ncaches:country:code:{code}"]
    CacheHit{"Cache HIT?"}
    ReturnCached["Return cached result\nimmediate response"]
    DBQuery["DB query\nsuspendTransaction SELECT"]
    CachePut["Redis SET\n(TTL 60s)"]
    ReturnDB["Return DB result"]

    UpdateReq["Request: update(record)"]
    CacheEvict["Redis DEL\ncache invalidation"]
    DBUpdate["DB update\nsuspendTransaction UPDATE"]

    Request --> CacheGet
    CacheGet --> CacheHit
    CacheHit -- HIT --> ReturnCached
    CacheHit -- MISS --> DBQuery
    DBQuery --> CachePut
    CachePut --> ReturnDB

    UpdateReq --> CacheEvict
    CacheEvict --> DBUpdate

    classDef blue   fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    classDef green  fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    classDef orange fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    classDef red    fill:#FFEBEE,stroke:#EF9A9A,color:#C62828
    class Request,UpdateReq blue
    class CacheGet,CachePut,CacheEvict orange
    class DBQuery,DBUpdate green
    class ReturnCached,ReturnDB green
    class CacheHit orange
```

## Project Structure

```
src/main/kotlin/exposed/r2dbc/examples/suspendedcache/
├── SpringSuspendedCacheApplication.kt           # Spring Boot application entry point
├── cache/
│   ├── LettuceSuspendedCache.kt                 # Lettuce Coroutines-based cache implementation
│   └── LettuceSuspendedCacheManager.kt          # Cache instance manager
├── config/
│   ├── ExposedR2dbcConfig.kt                    # R2DBC Database and ConnectionPool configuration
│   ├── LettuceCacheConfig.kt                    # Redis client and CacheManager configuration
│   ├── NettyConfig.kt                           # Netty server tuning
│   └── R2dbcRepositoryConfig.kt                 # Repository Bean registration (Default/Cached)
├── controller/
│   ├── DefaultCountryController.kt              # Direct DB query API (/default/countries)
│   └── CachedCountryController.kt               # Redis cache API (/cached/countries)
├── domain/
│   ├── model/
│   │   └── CountrySchema.kt                     # CountryTable definition + CountryRecord DTO + Mapper
│   └── repository/
│       ├── CountryR2dbcRepository.kt            # Repository interface
│       ├── DefaultCountryR2dbcRepository.kt     # Direct DB query implementation
│       └── CachedCountryR2dbcRepository.kt      # Redis cache + DB query (Decorator pattern)
└── utils/
    └── DataPopulator.kt                         # Insert 249 country code sample data on startup (runBlocking bridge pattern)
```

## Spring + Coroutine Bridge Pattern (`DataPopulator`)

`onApplicationEvent` in `ApplicationListener<ApplicationReadyEvent>` is a regular (non-suspend) function.
To use `suspendTransaction` from Exposed R2DBC, you must bridge to the coroutine world with `runBlocking`.

```kotlin
@Component
class DataPopulator: ApplicationListener<ApplicationReadyEvent> {

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        // runBlocking: blocks the current thread and runs coroutines (initialization-only pattern)
        // Dispatchers.IO: thread pool optimized for inserting 249 country code data
        runBlocking(Dispatchers.IO) {
            suspendTransaction {
                createTables()
                populateCountries()
            }
        }
    }
}
```

> **Note**: Use `runBlocking` only in initialization logic. In the Repository/Service layer,
> use `suspend fun` and `suspendTransaction` directly.

## Architecture

### Cache Layer Separation Using Decorator Pattern

Cache logic is separated from the Repository implementation and applied via the Decorator pattern.
`CachedCountryR2dbcRepository` wraps `DefaultCountryR2dbcRepository` to transparently apply Redis caching.

```
[Controller] → [CachedCountryR2dbcRepository] → [Redis Cache]
                         ↓ (cache miss)
              [DefaultCountryR2dbcRepository] → [R2DBC Database]
```

### Bean Registration Structure

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

Controllers select the desired Repository with `@Qualifier`.

## Database Schema

### CountryTable

```kotlin
object CountryTable: IntIdTable("countries") {
    val code = varchar("code", 2).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description", eagerLoading = true).nullable()
}
```

- 249 ISO country codes inserted as sample data
- `description` contains large text to make cache effects perceptible

## Core Implementation

### 1. LettuceSuspendedCache

Uses Lettuce's `RedisCoroutinesCommands` to operate Redis cache with `suspend` functions. Supports TTL-based automatic expiration.

```kotlin
class LettuceSuspendedCache<K: Any, V: Any>(
    val name: String,
    val commands: RedisCoroutinesCommands<String, V>,
    private val ttlSeconds: Long? = null,
) {
    suspend fun get(key: K): V? = commands.get(keyStr(key))

    suspend fun put(key: K, value: V) {
        if (ttlSeconds != null) commands.setex(keyStr(key), ttlSeconds, value)
        else commands.set(keyStr(key), value)
    }

    suspend fun evict(key: K) {
        commands.del(keyStr(key))
    }

    suspend fun clear() {
        val scanArgs = KeyScanArgs.Builder.matches("$name:*").limit(100)
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result = if (cursor == ScanCursor.INITIAL) commands.scan(scanArgs)
                         else commands.scan(cursor, scanArgs) ?: break
            result.keys.chunked(100).forEach { keys ->
                if (keys.isNotEmpty()) commands.unlink(*keys.toTypedArray())
            }
            cursor = result
        } while (!cursor.isFinished)
    }
}
```

### 2. LettuceSuspendedCacheManager

Manages cache instances by name via `getOrCreate()` and applies `LettuceBinaryCodec` (LZ4 + Fory serialization).

```kotlin
@Bean
fun lettuceSuspendedCacheManager(redisClient: RedisClient): LettuceSuspendedCacheManager {
    return LettuceSuspendedCacheManager(
        redisClient = redisClient,
        ttlSeconds = 60L,
        codec = LettuceBinaryCodecs.lz4Fory(),   // LZ4 compression + Fory serialization
    )
}

// Usage inside a cache-aware repository:
private val cache: LettuceSuspendedCache<String, CountryRecord> by lazy {
    cacheManager.getOrCreate(
        name = CACHE_NAME,
        ttlSeconds = 60,
    )
}
```

### 3. CachedCountryR2dbcRepository

Implements the Cache-Aside pattern:

- **Read**: Check cache → if miss, query DB and store in cache
- **Update**: Invalidate cache then update DB
- **Evict All**: Delete all keys matching the cache name pattern

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

## API Endpoints

### Default (Direct DB Query)

| Method | Path                        | Description                    |
|--------|-----------------------------|--------------------------------|
| GET    | `/default/countries/{code}` | Query by country code (no cache) |

### Cached (Redis Cache Applied)

| Method | Path                       | Description                         |
|--------|----------------------------|-------------------------------------|
| GET    | `/cached/countries/{code}` | Query by country code (Redis cache) |

Compare `/default` and `/cached` paths with the same API structure to observe caching effects.

## Running the Application

```bash
# Default run (H2 + Redis via Testcontainers)
./gradlew :07-spring-suspended-cache:bootRun

# Use PostgreSQL
./gradlew :07-spring-suspended-cache:bootRun --args='--spring.profiles.active=postgres'

# Use MySQL
./gradlew :07-spring-suspended-cache:bootRun --args='--spring.profiles.active=mysql'
```

Redis starts automatically via Testcontainers.

## Testing

```bash
./gradlew :07-spring-suspended-cache:test
```

### Test List

- **DefaultCountryR2dbcRepositoryTest** - Direct DB query Repository tests (repeated load, update)
- **CachedCountryR2dbcRepositoryTest** - Cached Repository tests (repeated load, update, full cache eviction)
- **DefaultCountryControllerTest** - Direct DB query API tests (sequential/parallel queries)
- **CachedCountryControllerTest** - Cached API tests (sequential/parallel queries)
- **ExposedR2dbcConfigTest** - R2DBC configuration load verification
- **LettuceCacheConfigTest** - Redis cache configuration load verification
- **R2dbcRepositoryConfigTest** - Repository Bean registration verification

Tests use `@RepeatedTest` to verify performance differences between the first (cold) query and subsequent (warm/cached) queries.

## How Suspended Cache Works (Detail)

### LettuceSuspendedCache Internal Flow

```
[Controller suspend fun]
        │
        ▼
[CachedCountryR2dbcRepository]
        │
        ├─ cache.get(code)          ← Redis GET "caches:country:code:<code>"
        │       │
        │       ├─ HIT  → return immediately (no DB call)
        │       │
        │       └─ MISS → delegate.findByCode(code)   ← Exposed R2DBC suspendTransaction
        │                       │
        │                       └─ cache.put(code, result)  ← Redis SET/SETEX (TTL 60s)
        │
        └─ cache.evict(code)        ← Redis DEL (invalidate on update)
```

### SCAN-Based Full Cache Eviction

Redis's `KEYS` command scans all keys at once and can temporarily block the Redis server on large datasets.
`LettuceSuspendedCache.clear()` solves this with a cursor-based `SCAN` + `UNLINK` pattern (using `KeyScanArgs`):

```
SCAN cursor MATCH "caches:country:code:*" COUNT 100
    → get 100 keys at a time via KeyScanArgs.Builder.matches(...).limit(100)
    → UNLINK key1 key2 ... (async deletion, safer than DEL)
    → repeat until cursor.isFinished
```

Unlike `DEL`, `UNLINK` releases memory in the background and does not block the Redis event loop.

### Serialization/Compression Strategy

| Setting             | Codec        | Compression | Characteristics                              |
|---------------------|--------------|-------------|----------------------------------------------|
| `lz4Fory()`         | Fory (Java)  | LZ4         | Fast serialization + moderate compression (default) |
| `lz4Kryo5()`        | Kryo5        | LZ4         | Slightly slower than Fory but wider type support |
| `snappyFory()`      | Fory         | Snappy      | Google compression (network transfer optimized) |
| `zstdFory()`        | Fory         | Zstd        | High compression ratio (saves storage space) |

Since `CountryRecord`'s `description` field contains large text, the compression effect is significant.

## Further Reading

- [Exposed with Spring Suspended Cache](https://debop.notion.site/Exposed-with-Suspended-Spring-Cache-1db2744526b080769d2ef307e4a3c6c9)
- [Spring Caching Abstraction](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html)
