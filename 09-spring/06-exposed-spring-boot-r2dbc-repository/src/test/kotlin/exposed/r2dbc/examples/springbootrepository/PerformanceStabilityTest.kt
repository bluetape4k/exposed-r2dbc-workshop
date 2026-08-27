package exposed.r2dbc.examples.springbootrepository

import exposed.r2dbc.examples.springbootrepository.config.ConnectionPoolLifecycle
import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.repository.ProductR2dbcRepository
import exposed.r2dbc.examples.springbootrepository.support.AbstractProductDatabaseTest
import exposed.r2dbc.examples.springbootrepository.support.RecordingConnectionFactory
import io.bluetape4k.logging.KLogging
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactoryOptions
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Mono
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull

/**
 * maxSize=1 pool에서 반복 CRUD/stream과 bounded contention의 안정성 계약을 검증합니다.
 *
 * 처리량 benchmark가 아니라 timeout, 실패, acquire/close 불균형, pool leak이 없는지만
 * 기록하고 검증합니다.
 */
@ResourceLock("issue-204-products-h2", mode = ResourceAccessMode.READ_WRITE)
class PerformanceStabilityTest : AbstractProductDatabaseTest() {

    companion object : KLogging()

    @Autowired
    private lateinit var repository: ProductR2dbcRepository

    @Autowired
    private lateinit var appConnectionPool: ConnectionPool

    @Autowired
    private lateinit var connectionFactoryOptions: ConnectionFactoryOptions

    @Test
    fun `max size one remains stable across repeated CRUD stream and concurrent callers`() = runTest {
        val pool = connectionPoolOf(appConnectionPool.unwrap()) {
            initialSize = 0
            minIdle = 0
            maxSize = 1
            maxAcquireTime = Duration.ofSeconds(1)
            maxCreateConnectionTime = Duration.ofSeconds(3)
            maxIdleTime = Duration.ofSeconds(1)
            maxLifeTime = Duration.ofSeconds(3)
            acquireRetry = 0
        }
        val recorder = RecordingConnectionFactory(pool)
        val options = connectionFactoryOptions
        val database = R2dbcDatabase.connect(
            recorder,
            R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions { from(options) }
            },
        )

        try {
            val cycleMetrics = buildList {
                repeat(5) { cycle ->
                    val acquiredBefore = recorder.acquiredCount
                    val closedBefore = recorder.closedCount
                    val elapsed = measureTime {
                        val saved = withTimeout(5.seconds) {
                            suspendTransaction(db = database) {
                                repository.save(ProductRecord(null, "stability-$cycle", "bounded"))
                            }
                        }
                        val id = saved.id.shouldNotBeNull()
                        val found = withTimeout(5.seconds) {
                            suspendTransaction(db = database) {
                                repository.findByIdOrNull(id)
                            }
                        }
                        found shouldBeEqualTo saved

                        val streamed = withTimeout(5.seconds) {
                            repository.streamAll(database).take(1).toList()
                        }
                        streamed.size shouldBeEqualTo 1

                        withTimeout(5.seconds) {
                            suspendTransaction(db = database) {
                                repository.deleteById(id)
                            }
                        }
                    }
                    val acquiredDelta = recorder.acquiredCount - acquiredBefore
                    val closedDelta = recorder.closedCount - closedBefore
                    acquiredDelta shouldBeEqualTo closedDelta
                    recorder.openCount shouldBeEqualTo 0
                    add(
                        PoolMetric(
                            label = "cycle-$cycle",
                            elapsedMillis = elapsed.inWholeMilliseconds,
                            acquiredDelta = acquiredDelta,
                            closedDelta = closedDelta,
                            failedCount = 0,
                        ),
                    )
                }
            }

            val contentionAcquiredBefore = recorder.acquiredCount
            val contentionClosedBefore = recorder.closedCount
            val failedCount = AtomicInteger()
            val contentionElapsed = withContext(Dispatchers.Default) {
                measureTime {
                    withTimeout(5.seconds) {
                        coroutineScope {
                            (1..8).map {
                                async {
                                    try {
                                        val connection = Mono.from(recorder.create()).awaitSingle()
                                        try {
                                            delay(20)
                                        } finally {
                                            Mono.from(connection.close()).awaitSingleOrNull()
                                        }
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (_: Throwable) {
                                        failedCount.incrementAndGet()
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }
            }
            val contentionAcquiredDelta = recorder.acquiredCount - contentionAcquiredBefore
            val contentionClosedDelta = recorder.closedCount - contentionClosedBefore

            failedCount.get() shouldBeEqualTo 0
            contentionAcquiredDelta shouldBeEqualTo contentionClosedDelta
            recorder.openCount shouldBeEqualTo 0
            (contentionElapsed.inWholeMilliseconds >= 0).shouldBeTrue()

            log.info(
                "r2dbc_pool_stability cycles=$cycleMetrics contention=" +
                    PoolMetric(
                        label = "contention-8",
                        elapsedMillis = contentionElapsed.inWholeMilliseconds,
                        acquiredDelta = contentionAcquiredDelta,
                        closedDelta = contentionClosedDelta,
                        failedCount = failedCount.get(),
                    ),
            )
        } finally {
            ConnectionPoolLifecycle(database, pool).destroy()
        }
    }

    private data class PoolMetric(
        val label: String,
        val elapsedMillis: Long,
        val acquiredDelta: Int,
        val closedDelta: Int,
        val failedCount: Int,
    )
}
