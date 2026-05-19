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

![Execution Flow diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-sequence-01.png)

## Cache Class Structure

![Cache Class Structure diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-class-02.png)

## Cache-Aside Pattern Flow

![Cache-Aside Pattern Flow diagram](../../docs/images/readme-diagrams/09-spring-07-spring-suspended-cache-architecture-03.png)

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
