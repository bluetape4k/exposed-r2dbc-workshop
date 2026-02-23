package exposed.r2dbc.examples.routing.datasource

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * [RoutingKeyResolver]가 계산한 키를 이용해 대상 [ConnectionFactory]로 위임합니다.
 */
class DynamicRoutingConnectionFactory(
    private val registry: ConnectionFactoryRegistry,
    private val keyResolver: RoutingKeyResolver,
    private val defaultKey: String,
): ConnectionFactory {

    private val defaultFactory: ConnectionFactory =
        registry.get(defaultKey) ?: error("No ConnectionFactory for default key=$defaultKey")

    override fun create(): Publisher<out Connection> =
        Mono.deferContextual { context ->
            val key = keyResolver.currentLookupKey(context)
            val target = registry.get(key)
                ?: error("No ConnectionFactory for key=$key. keys=${registry.keys().sorted()}")
            Mono.from(target.create())
        }

    override fun getMetadata(): ConnectionFactoryMetadata = defaultFactory.metadata
}
