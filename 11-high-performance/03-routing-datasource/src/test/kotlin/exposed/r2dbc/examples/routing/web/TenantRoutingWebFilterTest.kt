package exposed.r2dbc.examples.routing.web

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import java.util.concurrent.atomic.AtomicReference

/**
 * [TenantRoutingWebFilter]가 Reactor Context에 라우팅 힌트를 적재하는지 검증한다.
 */
class TenantRoutingWebFilterTest {

    @Test
    fun `tenant 헤더와 readonly 경로를 Reactor Context에 기록한다`() {
        val filter = TenantRoutingWebFilter(defaultTenant = "default")
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/routing/marker/readonly")
                .header(TenantRoutingWebFilter.TENANT_HEADER, "acme")
        )
        val capturedContext = AtomicReference<ContextView>()
        val chain = WebFilterChain {
            Mono.deferContextual { context ->
                capturedContext.set(context)
                Mono.empty()
            }
        }

        filter.filter(exchange, chain).block()

        val context = requireNotNull(capturedContext.get())
        context.getOrDefault(RoutingContextKeys.TENANT, "") shouldBeEqualTo "acme"
        context.getOrDefault(RoutingContextKeys.READ_ONLY, false) shouldBeEqualTo true
    }

    @Test
    fun `빈 tenant 와 잘못된 readOnly 헤더는 기본값과 경로 규칙으로 보정한다`() {
        val filter = TenantRoutingWebFilter(defaultTenant = "default")
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/routing/marker")
                .header(TenantRoutingWebFilter.TENANT_HEADER, " ")
                .header(TenantRoutingWebFilter.READ_ONLY_HEADER, "not-a-boolean")
        )
        val capturedContext = AtomicReference<ContextView>()
        val chain = WebFilterChain {
            Mono.deferContextual { context ->
                capturedContext.set(context)
                Mono.empty()
            }
        }

        filter.filter(exchange, chain).block()

        val context = requireNotNull(capturedContext.get())
        context.getOrDefault(RoutingContextKeys.TENANT, "") shouldBeEqualTo "default"
        context.getOrDefault(RoutingContextKeys.READ_ONLY, true) shouldBeEqualTo false
    }
}
