package exposed.r2dbc.examples.springbootrepository.config

import exposed.r2dbc.examples.springbootrepository.ExposedSpringBootR2dbcRepositoryApp
import exposed.r2dbc.examples.springbootrepository.domain.Products
import exposed.r2dbc.examples.springbootrepository.service.ProductTransactionService
import exposed.r2dbc.examples.springbootrepository.support.AbstractProductDatabaseTest
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.seconds

/**
 * Flow 취소, bounded pool contention, pool lifecycle의 회복 계약을 검증합니다.
 */
@ResourceLock("issue-204-products-h2", mode = ResourceAccessMode.READ_WRITE)
class CancellationAndLifecycleTest : AbstractProductDatabaseTest() {

    @Autowired
    private lateinit var repository: exposed.r2dbc.examples.springbootrepository.repository.ProductR2dbcRepository

    @Autowired
    private lateinit var service: ProductTransactionService

    @Autowired
    private lateinit var appConnectionPool: ConnectionPool

    @Autowired
    private lateinit var connectionFactoryOptions: ConnectionFactoryOptions

    @Test
    fun `stream cancellation propagates and follow-up query succeeds`() = runTest {
        val child = launch {
            repository.streamAll(database).collect {
                currentCoroutineContext().cancel(CancellationException("test cancellation"))
            }
        }

        child.join()

        child.isCancelled.shouldBeTrue()
        child.getCancellationException().shouldBeInstanceOf<CancellationException>()
        (service.count() >= 2L).shouldBeTrue()
    }

    @Test
    fun `single connection pool serializes bounded concurrent callers`() = runTest {
        val pool = ConnectionPool(
            ConnectionPoolConfiguration.builder(appConnectionPool.unwrap())
                .maxSize(1)
                .maxAcquireTime(Duration.ofMillis(100))
                .maxCreateConnectionTime(Duration.ofSeconds(3))
                .maxIdleTime(Duration.ofSeconds(1))
                .maxLifeTime(Duration.ofSeconds(3))
                .acquireRetry(0)
                .build(),
        )

        try {
            withContext(Dispatchers.Default) {
                withTimeout(Duration.ofSeconds(5).toMillis()) {
                    coroutineScope {
                        (1..8).map {
                            async(Dispatchers.IO) {
                                val connection = pool.create().awaitSingle()
                                try {
                                    delay(5)
                                } finally {
                                    Mono.from(connection.close()).awaitSingleOrNull()
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        } finally {
            pool.dispose()
        }
    }

    @Test
    fun `lifecycle destroy is idempotent`() {
        val pool = mockk<ConnectionPool>(relaxed = true)
        val database = mockk<R2dbcDatabase>()
        every { pool.isDisposed } returns false
        val lifecycle = ConnectionPoolLifecycle(database, pool)

        lifecycle.destroy()
        lifecycle.destroy()

        verify(exactly = 1) { pool.dispose() }
    }

    @Test
    fun `disposed pool rejects later acquisition`() = runTest {
        val pool = connectionPoolOf(appConnectionPool.unwrap()) {
            initialSize = 0
            minIdle = 0
            maxSize = 1
            maxAcquireTime = Duration.ofMillis(100)
            acquireRetry = 0
        }
        val options = connectionFactoryOptions
        val database = R2dbcDatabase.connect(
            pool,
            R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions { from(options) }
            },
        )
        val lifecycle = ConnectionPoolLifecycle(database, pool)

        lifecycle.destroy()
        lifecycle.destroy()

        pool.isDisposed.shouldBeTrue()
        assertFailsWith<Throwable> { pool.create().awaitSingle() }
    }

    @Test
    fun `closing an isolated context unregisters database and allows a clean reopen`() {
        val context = AnnotationConfigApplicationContext().apply {
            environment.setActiveProfiles("h2")
            register(ExposedR2dbcConfig::class.java)
            refresh()
        }
        val database = context.getBean(R2dbcDatabase::class.java)
        val pool = context.getBean(ConnectionPool::class.java)

        TransactionManager.managerFor(database).shouldNotBeNull()
        pool.isDisposed.shouldBeFalse()

        context.close()

        pool.isDisposed.shouldBeTrue()
        assertFailsWith<IllegalStateException> { TransactionManager.managerFor(database) }

        val reopened = AnnotationConfigApplicationContext().apply {
            environment.setActiveProfiles("h2")
            register(ExposedR2dbcConfig::class.java)
            refresh()
        }
        try {
            reopened.getBean(R2dbcDatabase::class.java).shouldNotBeNull()
            reopened.getBean(ConnectionPool::class.java).isDisposed.shouldBeFalse()
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `initializer handles repeated ready events without duplicate fixture`() = runTest {
        val context = AnnotationConfigApplicationContext().apply {
            environment.setActiveProfiles("h2")
            register(ExposedR2dbcConfig::class.java)
            refresh()
        }
        try {
            val database = context.getBean(R2dbcDatabase::class.java)
            suspendTransaction(db = database) {
                SchemaUtils.drop(Products)
            }

            val initializer = ProductDataInitializer(database, Dispatchers.IO)
            val ready = ApplicationReadyEvent(
                SpringApplication(ExposedSpringBootR2dbcRepositoryApp::class.java),
                emptyArray(),
                context,
                Duration.ZERO,
            )
            initializer.onApplicationEvent(ready)
            initializer.onApplicationEvent(ready)

            val rows = suspendTransaction(db = database) {
                Products.selectAll().toList()
            }
            rows.size shouldBeEqualTo 1
            rows.first()[Products.name] shouldBeEqualTo "Notebook"
        } finally {
            context.close()
        }
    }
}

/**
 * 잘못된 driver 옵션과 credential sanitizer가 조용히 fallback하지 않는지 검증합니다.
 */
class InvalidR2dbcConfigurationTest {

    @Test
    fun `missing driver fails with a clear dialect error`() {
        val failure = assertFailsWith<IllegalStateException> {
            R2dbcDatabase.connect(
                R2dbcDatabaseConfig {
                    connectionFactoryOptions {
                        option(ConnectionFactoryOptions.PROTOCOL, "mem")
                        option(ConnectionFactoryOptions.DATABASE, "issue-204-missing-driver")
                    }
                }.build(),
            )
        }

        failure.message.orEmpty().contains("Unsupported driver dialect").shouldBeTrue()
    }

    @Test
    fun `unsupported driver fails with a clear dialect error`() {
        val failure = assertFailsWith<IllegalStateException> {
            R2dbcDatabase.connect(
                R2dbcDatabaseConfig {
                    connectionFactoryOptions {
                        option(ConnectionFactoryOptions.DRIVER, "unsupported-driver")
                    }
                }.build(),
            )
        }

        failure.message.orEmpty().contains("Unsupported driver dialect").shouldBeTrue()
    }

    @Test
    fun `unreachable H2 URL fails during connection acquisition`() = runTest {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(Option.valueOf("url"), "tcp://127.0.0.1:1/issue-204-invalid")
            .option(ConnectionFactoryOptions.USER, "sa")
            .option(ConnectionFactoryOptions.PASSWORD, "synthetic-secret")
            .build()
        val factory = ConnectionFactories.get(options)

        val failure = assertFailsWith<Throwable> {
            withTimeout(2.seconds) {
                Mono.from(factory.create()).awaitSingle()
            }
        }
        val details = failure.stackTraceToString()

        details.isNotBlank().shouldBeTrue()
        ("synthetic-secret" in details).shouldBeFalse()
    }

    @Test
    fun `sanitized summary excludes credentials and URL`() {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PASSWORD, "secret")
            .option(ConnectionFactoryOptions.HOST, "localhost")
            .option(ConnectionFactoryOptions.DATABASE, "regular")
            .build()

        val summary = options.sanitizedSummary()

        summary.contains("driver=h2").shouldBeTrue()
        summary.contains("host=localhost").shouldBeTrue()
        summary.contains("database=regular").shouldBeTrue()
        ("secret" !in summary).shouldBeTrue()
        ("password" !in summary.lowercase()).shouldBeTrue()
        ("r2dbc:" !in summary.lowercase()).shouldBeTrue()
    }
}
