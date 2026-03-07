package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord

/**
 * 국가 정보를 조회/갱신하고 관련 캐시를 무효화하는 저장소 계약입니다.
 */
interface CountryR2dbcRepository {

    /**
     * 국가 코드로 단건을 조회합니다.
     */
    suspend fun findByCode(code: String): CountryRecord?

    /**
     * 국가 정보를 갱신하고 반영된 행 수를 반환합니다.
     */
    suspend fun update(countryRecord: CountryRecord): Int

    /**
     * 저장소가 관리하는 국가 캐시를 전체 비웁니다.
     */
    suspend fun evictCacheAll()
}
