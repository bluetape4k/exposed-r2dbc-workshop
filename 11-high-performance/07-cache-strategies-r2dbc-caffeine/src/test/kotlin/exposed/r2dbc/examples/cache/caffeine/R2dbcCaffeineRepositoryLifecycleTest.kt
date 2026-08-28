package exposed.r2dbc.examples.cache.caffeine

import exposed.r2dbc.examples.cache.caffeine.config.R2dbcCaffeineResources
import exposed.r2dbc.examples.cache.caffeine.domain.ProductRecord
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.exposed.cache.CacheHealthReport
import io.bluetape4k.exposed.cache.CacheWorkerState
import io.bluetape4k.exposed.cache.CacheWriteMode
import io.bluetape4k.exposed.r2dbc.caffeine.repository.AbstractR2dbcCaffeineRepository
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class R2dbcCaffeineRepositoryLifecycleTest {

    @Test
    fun `cancellation from database loader remains cancellation`() = runSuspendIO {
        val repository = BlockingRepository()
        try {
            assertFailsWith<CancellationException> {
                withTimeout(100.milliseconds) {
                    repository.get("blocked")
                }
            }
        } finally {
            repository.close()
        }
    }

    @Test
    fun `close drains write behind and is idempotent`() = runSuspendIO {
        val resources = newResources("close", CacheWriteMode.WRITE_BEHIND)
        resources.awaitInitialized()
        resources.repository.put("sku-1", ProductRecord("sku-1", "closed name", 2))

        resources.close()
        resources.repository.validateConsistency().workerState shouldBeEqualTo CacheWorkerState.STOPPED
        resources.close()
    }

    @Test
    fun `close rejects later write and rolls admission back`() = runSuspendIO {
        val resources = newResources("closed-admission", CacheWriteMode.WRITE_BEHIND)
        try {
            resources.awaitInitialized()
            resources.repository.close()

            assertFailsWith<IllegalStateException> {
                resources.repository.put("sku-1", ProductRecord("sku-1", "closed", 3))
            }
            resources.repository.cache.synchronous().getIfPresent("sku-1").shouldBeNull()
            resources.repository.validateConsistency().queueDepth shouldBeEqualTo 0
            resources.repository.close()
        } finally {
            resources.close()
        }
    }

    @Test
    fun `flush failure is visible and retains failed batch`() = runSuspendIO {
        val resources = newResources("flush-failure", CacheWriteMode.WRITE_BEHIND)
        try {
            resources.awaitInitialized()
            resources.repository.put("sku-1", ProductRecord("sku-1", "x".repeat(121), 2))
            val health = withTimeout(5_000.milliseconds) {
                suspend fun poll(): CacheHealthReport = resources.repository.validateConsistency().let { current ->
                    if (current.lastFlushError != null) {
                        current
                    } else {
                        kotlinx.coroutines.delay(10)
                        poll()
                    }
                }
                poll()
            }
            health.lastFlushError.shouldNotBeNull()
            health.queueDepth shouldBeEqualTo 1
            resources.repository.invalidate("sku-1")
            resources.repository.findByIdFromDb("sku-1")?.name shouldBeEqualTo "Product 1"

            resources.repository.close()
            resources.repository.validateConsistency().workerState shouldBeEqualTo CacheWorkerState.FAILED
            resources.repository.validateConsistency().queueDepth shouldBeEqualTo 1
        } finally {
            resources.close()
        }
    }

    private fun newResources(slug: String, mode: CacheWriteMode): R2dbcCaffeineResources =
        R2dbcCaffeineResources.create(
            databaseName = "r2dbc_caffeine_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            writeMode = mode,
        )

    private class BlockingRepository : AbstractR2dbcCaffeineRepository<String, ProductRecord>() {
        private val blocked = CompletableDeferred<Unit>()

        override val table: IdTable<String>
            get() = error("table is not used by the cancellation fixture")

        override suspend fun ResultRow.toEntity(): ProductRecord = error("not used")

        override fun UpdateStatement.updateEntity(entity: ProductRecord) = error("not used")

        override fun BatchInsertStatement.insertEntity(entity: ProductRecord) = error("not used")

        override suspend fun findByIdFromDb(id: String): ProductRecord? {
            blocked.await()
            return null
        }

    }
}
