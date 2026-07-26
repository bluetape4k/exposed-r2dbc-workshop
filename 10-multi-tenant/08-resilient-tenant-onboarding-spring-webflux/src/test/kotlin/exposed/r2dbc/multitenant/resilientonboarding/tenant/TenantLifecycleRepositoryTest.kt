package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TenantLifecycleRepositoryTest {

    private lateinit var repository: TenantLifecycleRepository

    private val now: Instant = Instant.parse("2026-07-26T00:00:00Z")

    @BeforeEach
    fun setUp() = runSuspendIO {
        val database = R2dbcDatabase.connect(
            ConnectionFactories.get("r2dbc:h2:mem:///tenant_lifecycle_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )
        repository = TenantLifecycleRepository(database, leaseDuration = java.time.Duration.ofMinutes(2))
        repository.initializeSchema()
        repository.deleteAll()
    }

    @Test
    fun `new claim owns the first lifecycle attempt`() = runSuspendIO {
        val claim = repository.claim(TenantId("acme"), "Acme", now)

        val owner = assertIs<TenantClaim.Owner>(claim)
        assertEquals(TenantLifecycleStatus.PROVISIONING, owner.metadata.status)
        assertEquals(1, owner.metadata.attempt)
        assertEquals(now.plusSeconds(120), owner.metadata.leaseExpiresAt)
    }

    @Test
    fun `stale reservation cannot fail newer retry`() = runSuspendIO {
        val first = assertIs<TenantClaim.Owner>(repository.claim(TenantId("acme"), "Acme", now))
        assertTrue(repository.markFailed(first, TenantFailureCode.SCHEMA, now))
        val retry = assertIs<TenantClaim.Owner>(repository.claim(TenantId("acme"), "Acme", now.plusSeconds(1)))

        assertFalse(repository.markFailed(first, TenantFailureCode.POOL, now.plusSeconds(2)))
        assertEquals(retry.metadata.reservationToken, repository.find(TenantId("acme"))?.reservationToken)
        assertEquals(2, repository.find(TenantId("acme"))?.attempt)
    }

    @Test
    fun `concurrent claims yield one owner and pending followers`() = runSuspendIO {
        val claims = coroutineScope {
            List(12) {
                async { repository.claim(TenantId("parallel"), "Parallel", now) }
            }.awaitAll()
        }

        assertEquals(1, claims.filterIsInstance<TenantClaim.Owner>().size)
        assertEquals(11, claims.filterIsInstance<TenantClaim.Pending>().size)
    }
}
