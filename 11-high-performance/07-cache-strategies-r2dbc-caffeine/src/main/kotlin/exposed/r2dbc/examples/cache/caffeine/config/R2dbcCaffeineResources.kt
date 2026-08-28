package exposed.r2dbc.examples.cache.caffeine.config

import exposed.r2dbc.examples.cache.caffeine.domain.ProductCaffeineRepository
import exposed.r2dbc.examples.cache.caffeine.domain.ProductDataInitializer
import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.exposed.cache.CacheWriteMode
import io.bluetape4k.exposed.cache.LocalCacheConfig
import io.bluetape4k.exposed.r2dbc.caffeine.repository.AbstractR2dbcCaffeineRepository
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 한 Ktor 애플리케이션이 소유하는 H2 pool, Exposed database, Caffeine repository입니다.
 * write-behind 최종 반영을 위해 repository를 먼저 닫고 manager와 pool을 순서대로 해제합니다.
 */
class R2dbcCaffeineResources private constructor(
    private val pool: ConnectionPool,
    val database: R2dbcDatabase,
    val repository: ProductCaffeineRepository,
    private val previousDefaultDatabase: R2dbcDatabase?,
    private val initializerScope: CoroutineScope,
    private val initialized: kotlinx.coroutines.Deferred<Unit>,
): AutoCloseable {

    private val closed = AtomicBoolean(false)

    /** schema와 seed 초기화가 완료될 때까지 기다립니다. */
    suspend fun awaitInitialized() {
        initialized.await()
    }

    /** repository → Exposed manager → pool 순서로 애플리케이션 자원을 한 번 해제합니다. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runBlocking {
            initializerScope.coroutineContext[Job]?.let { job ->
                job.cancelAndJoin()
            }
        }
        try {
            repository.close()
        } finally {
            try {
                TransactionManager.closeAndUnregister(database)
            } finally {
                if (TransactionManager.defaultDatabase == database) {
                    TransactionManager.defaultDatabase = previousDefaultDatabase
                }
                pool.dispose()
                initializerScope.cancel()
            }
        }
    }

    companion object {
        /** 고유한 H2 R2DBC pool과 provider repository를 생성합니다. */
        fun create(
            databaseName: String = "r2dbc_caffeine",
            writeMode: CacheWriteMode = CacheWriteMode.WRITE_THROUGH,
            maxPoolSize: Int = 4,
        ): R2dbcCaffeineResources {
            val options = h2ConnectionFactoryOptions(database = databaseName)
            val pool = connectionPoolOf(options) {
                maxSize = maxPoolSize
                initialSize = minOf(2, maxPoolSize)
                minIdle = 0
                maxCreateConnectionTime = Duration.ofSeconds(5)
                maxAcquireTime = Duration.ofSeconds(3)
            }
            val config = R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions = options
            }
            val database = R2dbcDatabase.connect(pool, config)
            val previousDefaultDatabase = TransactionManager.defaultDatabase
            TransactionManager.defaultDatabase = database

            val repository = ProductCaffeineRepository(
                LocalCacheConfig(
                    keyPrefix = "r2dbc:caffeine:products",
                    writeMode = writeMode,
                    writeBehindBatchSize = 2,
                    writeBehindQueueCapacity = 8,
                )
            )
            val initializerScope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineName("r2dbc-caffeine-initializer")
            )
            val initialized = initializerScope.async {
                ProductDataInitializer(database).initialize()
            }

            return R2dbcCaffeineResources(
                pool = pool,
                database = database,
                repository = repository,
                previousDefaultDatabase = previousDefaultDatabase,
                initializerScope = initializerScope,
                initialized = initialized,
            )
        }
    }
}
