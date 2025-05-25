package exposed.r2dbc.examples.suspendedcache.config

import exposed.r2dbc.examples.suspendedcache.cache.LettuceSuspendedCacheManager
import exposed.r2dbc.examples.suspendedcache.domain.repository.CachedCountryR2dbcRepository
import exposed.r2dbc.examples.suspendedcache.domain.repository.CountryR2dbcRepository
import exposed.r2dbc.examples.suspendedcache.domain.repository.DefaultCountryR2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class R2dbcRepositoryConfig(
    private val suspendedCacheManager: LettuceSuspendedCacheManager,
) {

    companion object: KLoggingChannel()

    @Bean(name = ["countryR2dbcRepository", "defaultCountryR2dbcRepository"])
    fun countryR2dbcRepository(): CountryR2dbcRepository {
        return DefaultCountryR2dbcRepository()
    }

    @Bean(name = ["cachedCountryR2dbcRepository"])
    fun cachedCountryR2dbcRepository(countryR2dbcRepository: CountryR2dbcRepository): CountryR2dbcRepository {
        return CachedCountryR2dbcRepository(
            delegate = countryR2dbcRepository,
            cacheManager = suspendedCacheManager,
        )
    }
}
