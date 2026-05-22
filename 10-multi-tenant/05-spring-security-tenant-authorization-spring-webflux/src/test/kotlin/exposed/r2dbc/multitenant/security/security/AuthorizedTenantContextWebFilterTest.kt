package exposed.r2dbc.multitenant.security.security

import exposed.r2dbc.multitenant.security.tenant.TenantContextKeys
import exposed.r2dbc.multitenant.security.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

class AuthorizedTenantContextWebFilterTest {

    private val filter = AuthorizedTenantContextWebFilter(TenantAuthenticationResolver())

    @Test
    fun `authorized tenant is written to reactor context`() = runSuspendIO {
        val seenTenantId = AtomicReference<String>()
        val exchange = exchange(Tenant.KOREAN.id)
        val chain = WebFilterChain {
            Mono.deferContextual { context ->
                seenTenantId.set(context.get(TenantContextKeys.TENANT_ID))
                Mono.empty()
            }
        }

        filter
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(TenantAuthenticationToken("test", Tenant.KOREAN)))
            .awaitSingleOrNull()

        seenTenantId.get() shouldBeEqualTo Tenant.KOREAN.id
    }

    @Test
    fun `mismatched authenticated tenant is denied`() = runSuspendIO {
        val exchange = exchange(Tenant.ENGLISH.id)
        val chain = WebFilterChain { Mono.empty() }

        assertFailsWith<AccessDeniedException> {
            filter
                .filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(TenantAuthenticationToken("test", Tenant.KOREAN)))
                .awaitSingleOrNull()
        }
    }

    private fun exchange(tenantId: String): MockServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest
                .get("/actors")
                .header(TENANT_HEADER, tenantId)
                .build()
        )
}
