package exposed.r2dbc.multitenant.resilientonboarding.controller

import exposed.r2dbc.multitenant.resilientonboarding.ResilientTenantOnboardingApp
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [ResilientTenantOnboardingApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class TenantOnboardingControllerTest(
    @Autowired private val webTestClient: WebTestClient,
) {

    @Test
    fun `new tenant is created then can be polled`() {
        webTestClient.post().uri("/api/admin/tenants")
            .header("X-ADMIN-TOKEN", "workshop-admin")
            .bodyValue(mapOf("tenantId" to "acme", "displayName" to "Acme"))
            .exchange()
            .expectStatus().isCreated
            .expectHeader().valueEquals("Location", "/api/tenants/acme")
            .expectBody()
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.attempt").isEqualTo(1)

        webTestClient.get().uri("/api/tenants/acme")
            .header("X-ADMIN-TOKEN", "workshop-admin")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.tenantId").isEqualTo("acme")
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `same active request is idempotent`() {
        val request = mapOf("tenantId" to "idem", "displayName" to "Idem")

        webTestClient.post().uri("/api/admin/tenants")
            .header("X-ADMIN-TOKEN", "workshop-admin")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated

        webTestClient.post().uri("/api/admin/tenants")
            .header("X-ADMIN-TOKEN", "workshop-admin")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.attempt").isEqualTo(1)
    }
}
