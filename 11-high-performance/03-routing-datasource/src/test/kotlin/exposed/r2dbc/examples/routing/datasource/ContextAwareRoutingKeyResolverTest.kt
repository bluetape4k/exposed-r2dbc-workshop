package exposed.r2dbc.examples.routing.datasource

import exposed.r2dbc.examples.routing.context.RoutingContextKeys
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import reactor.util.context.Context

class ContextAwareRoutingKeyResolverTest {

    private val resolver = ContextAwareRoutingKeyResolver(defaultTenant = "default")

    @Test
    fun `tenant와 readOnly가 주어지면 ro 키를 계산한다`() {
        val context = Context.of(
            RoutingContextKeys.TENANT, "acme",
            RoutingContextKeys.READ_ONLY, true,
        )

        resolver.currentLookupKey(context) shouldBeEqualTo "acme:ro"
    }

    @Test
    fun `컨텍스트가 비어있으면 default-rw 키를 계산한다`() {
        resolver.currentLookupKey(Context.empty()) shouldBeEqualTo "default:rw"
    }
}
