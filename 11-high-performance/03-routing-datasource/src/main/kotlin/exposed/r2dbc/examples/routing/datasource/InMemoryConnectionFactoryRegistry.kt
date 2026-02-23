package exposed.r2dbc.examples.routing.datasource

import io.r2dbc.spi.ConnectionFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * [ConnectionFactoryRegistry]의 스레드 안전한 인메모리 구현체입니다.
 */
class InMemoryConnectionFactoryRegistry: ConnectionFactoryRegistry {

    private val factories = ConcurrentHashMap<String, ConnectionFactory>()

    override fun register(key: String, connectionFactory: ConnectionFactory) {
        factories[key] = connectionFactory
    }

    override fun get(key: String): ConnectionFactory? = factories[key]

    override fun keys(): Set<String> = factories.keys
}
