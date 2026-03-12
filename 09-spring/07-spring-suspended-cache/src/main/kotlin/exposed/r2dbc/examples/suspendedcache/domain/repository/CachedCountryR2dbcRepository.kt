package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCache
import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel

/**
 * [CountryR2dbcRepository] 앞단에 Redis 캐시를 두는 Decorator 구현입니다.
 *
 * ## Cache-Aside (Lazy Loading) 패턴
 * ```
 * 읽기: Cache 조회 → miss 시 DB 조회 후 Cache 저장 → 결과 반환
 * 쓰기: Cache 무효화 → DB 업데이트
 * 전체 삭제: Cache namespace의 모든 키 SCAN+UNLINK
 * ```
 *
 * ## 구성
 * - [delegate]: 실제 DB 접근을 담당하는 [CountryR2dbcRepository] 구현체
 * - [cacheManager]: [LettuceSuspendedCache] 인스턴스를 이름(name)별로 관리
 * - TTL: 60초 (캐시 만료 시 자동으로 DB에서 재조회)
 *
 * ## 스레드 안전성
 * 모든 메서드는 `suspend` 함수로, Kotlin Coroutines 환경에서 비동기·비블로킹으로 동작합니다.
 *
 * @param delegate 실제 DB I/O를 수행하는 Repository
 * @param cacheManager Lettuce 기반 캐시 매니저
 * @see LettuceSuspendedCache
 * @see LettuceSuspendedCacheManager
 */
class CachedCountryR2dbcRepository(
    private val delegate: CountryR2dbcRepository,
    private val cacheManager: LettuceSuspendedCacheManager,
): CountryR2dbcRepository {

    companion object: KLoggingChannel() {
        const val CACHE_NAME = "caches:country:code"
    }

    private val cache: LettuceSuspendedCache<String, CountryRecord> by lazy {
        cacheManager.getOrCreate(
            name = CACHE_NAME,
            ttlSeconds = 60,
        )
    }

    /**
     * 국가 코드로 국가 정보를 조회합니다.
     *
     * 캐시 히트 시 Redis에서 즉시 반환하고, 미스 시 DB에서 조회 후 캐시에 저장합니다.
     *
     * @param code ISO 2자리 국가 코드 (예: "KR", "US")
     * @return 국가 정보, 없으면 `null`
     */
    override suspend fun findByCode(code: String): CountryRecord? {
        return cache.get(code)
            ?: delegate.findByCode(code)?.apply { cache.put(code, this) }
    }

    /**
     * 국가 정보를 업데이트합니다.
     *
     * 캐시 일관성을 보장하기 위해 DB 업데이트 전에 해당 키의 캐시를 먼저 무효화합니다.
     *
     * @param countryRecord 업데이트할 국가 정보
     * @return 업데이트된 행 수
     */
    override suspend fun update(countryRecord: CountryRecord): Int {
        cache.evict(countryRecord.code)
        return delegate.update(countryRecord)
    }

    /**
     * 현재 캐시 네임스페이스(`caches:country:code:*`)의 모든 캐시를 삭제합니다.
     *
     * SCAN 기반으로 100개씩 배치 처리하므로 대량 키 환경에서도 Redis 메인 스레드를 차단하지 않습니다.
     */
    override suspend fun evictCacheAll() {
        cache.clear()
    }
}
