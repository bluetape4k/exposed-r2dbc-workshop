package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicReference

class TenantFilterTest {

    @Test
    fun `valid tenant header is written to reactor context`() {
        val seenTenantId = AtomicReference<String>()
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest
                .get("/actors")
                .header(TenantFilter.TENANT_HEADER, " korean ")
                .build(),
        )
        val chain = WebFilterChain {
            Mono.deferContextual { context ->
                seenTenantId.set(context.get(TenantContextKeys.TENANT_ID))
                Mono.empty()
            }
        }

        StepVerifier
            .create(TenantFilter().filter(exchange, chain))
            .verifyComplete()

        seenTenantId.get() shouldBeEqualTo Tenants.Tenant.KOREAN.id
    }
}
