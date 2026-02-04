package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord
import exposed.r2dbc.examples.suspendedcache.domain.model.CountryTable
import exposed.r2dbc.examples.suspendedcache.domain.model.toCountryRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

class DefaultCountryR2dbcRepository: CountryR2dbcRepository {

    companion object: KLoggingChannel()

    override suspend fun findByCode(code: String): CountryRecord? = suspendTransaction {
        CountryTable.selectAll()
            .where { CountryTable.code eq code }
            .singleOrNull()
            ?.toCountryRecord()
    }

    override suspend fun update(countryRecord: CountryRecord): Int = suspendTransaction {
        CountryTable.update({ CountryTable.code eq countryRecord.code }) {
            it[name] = countryRecord.name
            it[description] = countryRecord.description
        }
    }

    override suspend fun evictCacheAll() {
        // Nothing to do.
    }
}
