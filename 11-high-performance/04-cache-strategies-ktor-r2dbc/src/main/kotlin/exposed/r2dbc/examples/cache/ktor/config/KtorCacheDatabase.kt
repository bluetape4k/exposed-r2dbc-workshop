package exposed.r2dbc.examples.cache.ktor.config

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import java.time.Duration

/**
 * Owns the R2DBC pool used by the Ktor cache strategy example.
 */
class KtorCacheDatabase private constructor(
    private val pool: ConnectionPool,
    val database: R2dbcDatabase,
): AutoCloseable {

    override fun close() {
        pool.dispose()
    }

    companion object {
        /**
         * Creates an H2 in-memory R2DBC pool for the cache strategy example.
         */
        fun create(
            databaseName: String = "ktor_cache_strategies",
            maxPoolSize: Int = 8,
        ): KtorCacheDatabase {
            val options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "h2")
                .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                .option(ConnectionFactoryOptions.DATABASE, databaseName)
                .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
                .option(Option.valueOf("DB_CLOSE_ON_EXIT"), "FALSE")
                .build()
            val pool = ConnectionPool(
                ConnectionPoolConfiguration.builder(ConnectionFactories.get(options))
                    .maxSize(maxPoolSize)
                    .maxCreateConnectionTime(Duration.ofSeconds(5))
                    .maxAcquireTime(Duration.ofSeconds(3))
                    .build()
            )
            val config = R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions = options
            }
            return KtorCacheDatabase(pool, R2dbcDatabase.connect(pool, config))
        }
    }
}
