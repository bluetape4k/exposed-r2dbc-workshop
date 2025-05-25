package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO

interface CountryR2dbcRepository {

    suspend fun findByCode(code: String): CountryDTO?

    suspend fun update(countryDTO: CountryDTO): Int

    suspend fun evictCacheAll()
}
