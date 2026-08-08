package exposed.r2dbc.examples.routing.ktor.routing

import exposed.r2dbc.shared.config.h2ConnectionFactoryOptions
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import java.time.Duration

/**
 * Owns one physical R2DBC target used by the routing datasource example.
 */
class RoutingDatabase private constructor(
    val routingKey: String,
    private val pool: ConnectionPool,
    val database: R2dbcDatabase,
): AutoCloseable {

    override fun close() {
        pool.dispose()
    }

    companion object {
        /**
         * Creates an H2 in-memory R2DBC pool for one routing key.
         */
        fun create(
            routingKey: String,
            databaseName: String,
            maxPoolSize: Int = 4,
        ): RoutingDatabase {
            val options = h2ConnectionFactoryOptions(database = databaseName)
            val pool = connectionPoolOf(options) {
                maxSize = maxPoolSize
                initialSize = minOf(10, maxPoolSize)
                minIdle = 0
                maxCreateConnectionTime = Duration.ofSeconds(5)
                maxAcquireTime = Duration.ofSeconds(3)
            }
            val config = R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions = options
            }
            return RoutingDatabase(
                routingKey = routingKey,
                pool = pool,
                database = R2dbcDatabase.connect(pool, config),
            )
        }
    }
}
