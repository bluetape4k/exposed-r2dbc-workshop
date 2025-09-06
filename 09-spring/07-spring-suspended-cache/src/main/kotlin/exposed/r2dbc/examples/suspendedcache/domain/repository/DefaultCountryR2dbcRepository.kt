package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import exposed.r2dbc.examples.suspendedcache.domain.CountryTable
import exposed.r2dbc.examples.suspendedcache.domain.toCountryDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

class DefaultCountryR2dbcRepository: CountryR2dbcRepository {

    companion object: KLoggingChannel()

    override suspend fun findByCode(code: String): CountryDTO? = suspendTransaction {
        CountryTable.selectAll()
            .where { CountryTable.code eq code }
            .singleOrNull()
            ?.toCountryDTO()
    }

    override suspend fun update(countryDTO: CountryDTO): Int = suspendTransaction {
        CountryTable.update({ CountryTable.code eq countryDTO.code }) {
            it[name] = countryDTO.name
            it[description] = countryDTO.description
        }
    }

    override suspend fun evictCacheAll() {
        // Nothing to do.
    }
}
