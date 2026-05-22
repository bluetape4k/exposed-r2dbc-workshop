package exposed.r2dbc.multitenant.onboarding.controller

import exposed.r2dbc.multitenant.onboarding.AbstractTenantOnboardingTest
import exposed.r2dbc.multitenant.onboarding.tenant.TenantHeaders
import exposed.r2dbc.multitenant.onboarding.tenant.TenantId
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingResponse
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import java.util.UUID

@TestPropertySource(
    properties = [
        "app.onboarding.max-tenants=1",
    ]
)
class TenantLimitControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractTenantOnboardingTest() {

    @Test
    fun `tenant cap returns too many requests`() = runSuspendIO {
        onboard(nextTenantId())

        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to nextTenantId().value, "displayName" to "Tenant Limit"))
            .exchange()
            .expectStatus().isEqualTo(429)
    }

    private suspend fun onboard(tenantId: TenantId): TenantOnboardingResponse =
        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to tenantId.value, "displayName" to "Tenant ${tenantId.value}"))
            .exchange()
            .expectStatus().isCreated
            .returnResult<TenantOnboardingResponse>().responseBody
            .awaitSingle()

    private fun nextTenantId(): TenantId =
        TenantId("tenant${UUID.randomUUID().toString().replace("-", "").take(8)}")

    private companion object {
        const val ADMIN_TOKEN = "workshop-admin"
    }
}
