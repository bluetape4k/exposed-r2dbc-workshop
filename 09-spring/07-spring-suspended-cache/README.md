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
| Codec     | Fory, Kryo5                    |
| 압축        | LZ4, Snappy, Zstd              |
| DB        | H2 (기본), MySQL 8, PostgreSQL   |
| 컨테이너      | Testcontainers (DB + Redis)    |
| 서버        | Netty (Reactive)               |

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

```
[Controller] → [CachedCountryR2dbcRepository] → [Redis Cache]
                         ↓ (cache miss)
              [DefaultCountryR2dbcRepository] → [R2DBC Database]
```

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
        commands.keys("$name:*")
            .chunked(100, true)
            .collect { keys -> commands.del(*keys.toTypedArray()) }
    }
}
```

### 2. LettuceSuspendedCacheManager

캐시 인스턴스를 이름(name)별로 관리하며, `LettuceBinaryCodec`(LZ4 + Fory 직렬화)을 적용합니다.

```kotlin
@Bean
fun lettuceSuspendedCacheManager(redisClient: RedisClient): LettuceSuspendedCacheManager {
    return LettuceSuspendedCacheManager(
        redisClient = redisClient,
        ttlSeconds = 60L,
        codec = LettuceBinaryCodecs.lz4Fory(),   // LZ4 압축 + Fory 직렬화
    )
}
```

### 3. CachedCountryR2dbcRepository

Cache-Aside 패턴을 구현합니다:

- **Read**: 캐시 조회 -> miss 시 DB 조회 후 캐시 저장
- **Update**: 캐시 무효화 후 DB 업데이트
- **Evict All**: 해당 캐시 이름 패턴의 모든 키 삭제

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

```
[Controller suspend fun]
        │
        ▼
[CachedCountryR2dbcRepository]
        │
        ├─ cache.get(code)          ← Redis GET "caches:country:code:<code>"
        │       │
        │       ├─ HIT  → 즉시 반환 (DB 호출 없음)
        │       │
        │       └─ MISS → delegate.findByCode(code)   ← Exposed R2DBC suspendTransaction
        │                       │
        │                       └─ cache.put(code, result)  ← Redis SET/SETEX (TTL 60s)
        │
        └─ cache.evict(code)        ← Redis DEL (업데이트 시 무효화)
```

### SCAN 기반 전체 캐시 삭제

Redis의 `KEYS` 명령은 모든 키를 한 번에 스캔하므로 대규모 데이터셋에서 Redis 서버를 일시적으로 블로킹할 수 있습니다.
`LettuceSuspendedCache.clear()`는 `SCAN` + `UNLINK` 패턴으로 이 문제를 해결합니다:

```
SCAN cursor MATCH "caches:country:code:*" COUNT 100
    → 키 목록 100개씩 취득
    → UNLINK key1 key2 ... (비동기 삭제, DEL보다 안전)
    → cursor가 "0"이 될 때까지 반복
```

`UNLINK`는 `DEL`과 달리 백그라운드에서 메모리를 해제하므로 Redis 이벤트 루프를 차단하지 않습니다.

### 직렬화/압축 전략

| 설정                  | Codec              | 압축  | 특징                           |
|---------------------|--------------------|-----|------------------------------|
| `lz4Fory()`         | Fory (Java)        | LZ4 | 빠른 직렬화 + 중간 압축 (기본값)         |
| `lz4Kryo5()`        | Kryo5              | LZ4 | Fory보다 약간 느리지만 더 광범위한 타입 지원  |
| `snappyFory()`      | Fory               | Snappy | Google 압축 (네트워크 전송 최적화)   |
| `zstdFory()`        | Fory               | Zstd | 높은 압축률 (저장 공간 절약)            |

`CountryRecord`의 `description` 필드에 대용량 텍스트가 포함되므로 압축 효과가 큽니다.

## Further Reading

- [Exppose with Spring Suspended Cache](https://debop.notion.site/Exposed-with-Suspended-Spring-Cache-1db2744526b080769d2ef307e4a3c6c9)
- [Spring Caching Abstraction](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html)
