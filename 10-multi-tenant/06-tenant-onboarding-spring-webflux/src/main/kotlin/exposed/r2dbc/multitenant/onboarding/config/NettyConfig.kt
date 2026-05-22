package exposed.r2dbc.multitenant.onboarding.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.utils.Runtimex
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory
import org.springframework.boot.reactor.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ReactorResourceFactory
import reactor.netty.http.server.HttpServer
import reactor.netty.resources.ConnectionProvider
import reactor.netty.resources.LoopResources
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Configures Netty server resources for the tenant onboarding WebFlux example.
 */
@Configuration
class NettyConfig {
    companion object: KLoggingChannel()

    /**
     * Registers custom Netty server options with [NettyReactiveWebServerFactory].
     */
    @Bean
    fun nettyReactiveWebServerFactory(): NettyReactiveWebServerFactory {
        return NettyReactiveWebServerFactory().apply {
            addServerCustomizers(EventLoopNettyCustomizer())
        }
    }

    /**
     * Applies connection limits, keep-alive, backlog, and read/write timeouts.
     */
    class EventLoopNettyCustomizer: NettyServerCustomizer {
        override fun apply(httpServer: HttpServer): HttpServer {
            return httpServer
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_BACKLOG, 8_000)
                .doOnConnection { conn ->
                    conn.addHandlerLast(ReadTimeoutHandler(30))
                    conn.addHandlerLast(WriteTimeoutHandler(30))
                }
        }
    }

    @Bean
    fun reactorResourceFactory(): ReactorResourceFactory {
        return ReactorResourceFactory().apply {
            isUseGlobalResources = false
            connectionProvider = ConnectionProvider.builder("http")
                .maxConnections(8_000)
                .maxIdleTime(30.seconds.toJavaDuration())
                .build()

            loopResources = LoopResources.create(
                "event-loop",
                8,
                maxOf(Runtimex.availableProcessors * 8, 64),
                true
            )
        }
    }
}
