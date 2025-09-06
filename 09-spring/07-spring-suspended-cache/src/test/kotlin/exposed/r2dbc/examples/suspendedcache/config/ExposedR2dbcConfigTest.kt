package exposed.r2dbc.examples.suspendedcache.config

import exposed.r2dbc.examples.suspendedcache.AbstractSpringSuspendedCacheApplicationTest
import exposed.r2dbc.examples.suspendedcache.domain.CountryTable
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test

class ExposedR2dbcConfigTest: AbstractSpringSuspendedCacheApplicationTest() {

    companion object: KLoggingChannel()

    @Test
    fun `Schema 가 자동 생성되어야 한다`() = runTest {
        suspendTransaction {
            CountryTable.exists().shouldBeTrue()
        }
    }

    @Test
    fun `CountryCodeTable 이 생성되고 데이터가 입력되어 있어야 한다`() = runTest {
        suspendTransaction {
            CountryTable.selectAll().count() shouldBeGreaterThan 0
        }
    }
}
