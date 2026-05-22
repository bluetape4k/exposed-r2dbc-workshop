package exposed.r2dbc.multitenant.ktor.config

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
 * Owns the R2DBC pool used by the standalone Ktor multi-tenant example.
 */
class KtorMultitenantDatabase private constructor(
    private val pool: ConnectionPool,
    val database: R2dbcDatabase,
): AutoCloseable {

    override fun close() {
        pool.dispose()
    }

    companion object {
        /**
         * Creates an H2 in-memory R2DBC pool with a stable database name.
         */
        fun create(
            databaseName: String = "ktor_multitenant",
            maxPoolSize: Int = 8,
        ): KtorMultitenantDatabase {
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
                // Exposed uses the options for dialect metadata while the pool owns connection acquisition.
                connectionFactoryOptions = options
            }
            return KtorMultitenantDatabase(pool, R2dbcDatabase.connect(pool, config))
        }
    }
}
