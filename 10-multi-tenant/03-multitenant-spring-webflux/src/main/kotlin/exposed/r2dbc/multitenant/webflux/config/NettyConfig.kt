package exposed.r2dbc.multitenant.webflux.config

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.utils.Runtimex
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ReactorResourceFactory
import reactor.netty.http.server.HttpServer
import reactor.netty.resources.ConnectionProvider
import reactor.netty.resources.LoopResources
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * 멀티테넌트 Spring WebFlux 애플리케이션에서 사용하는 Netty 서버 관련 설정을 제공합니다.
 *
 * 이벤트 루프 스레드 수, 커넥션 풀, SO_KEEPALIVE/SO_BACKLOG,
 * 읽기·쓰기 타임아웃을 구성하여 고성능 비동기 서버를 설정합니다.
 */
@Configuration
class NettyConfig {
    companion object: KLoggingChannel()

    /**
     * [NettyReactiveWebServerFactory]에 [EventLoopNettyCustomizer]를 적용하여
     * 커스텀 Netty 서버 설정을 등록합니다.
     */
    @Bean
    fun nettyReactiveWebServerFactory(): NettyReactiveWebServerFactory {
        return NettyReactiveWebServerFactory().apply {
            addServerCustomizers(EventLoopNettyCustomizer())
        }
    }

    /**
     * Netty HTTP 서버에 SO_KEEPALIVE, SO_BACKLOG, 읽기/쓰기 타임아웃을 적용하는 커스터마이저입니다.
     *
     * [NettyServerCustomizer]를 구현하여 멀티테넌트 환경에 맞는 Netty 서버 옵션을 제어합니다.
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
                kotlin.comparisons.maxOf(Runtimex.availableProcessors * 8, 64),
                true
            )
        }
    }
}
