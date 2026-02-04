package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord

interface CountryR2dbcRepository {

    suspend fun findByCode(code: String): CountryRecord?

    suspend fun update(countryRecord: CountryRecord): Int

    suspend fun evictCacheAll()
}
