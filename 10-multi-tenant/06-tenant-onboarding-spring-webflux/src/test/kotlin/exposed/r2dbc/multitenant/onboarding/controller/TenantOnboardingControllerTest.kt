package exposed.r2dbc.multitenant.onboarding.controller

import exposed.r2dbc.multitenant.onboarding.AbstractTenantOnboardingTest
import exposed.r2dbc.multitenant.onboarding.ProvisioningFailureSwitch
import exposed.r2dbc.multitenant.onboarding.domain.model.ActorRecord
import exposed.r2dbc.multitenant.onboarding.tenant.ProvisioningFailurePoint
import exposed.r2dbc.multitenant.onboarding.tenant.TenantConnectionFactoryRegistry
import exposed.r2dbc.multitenant.onboarding.tenant.TenantHeaders
import exposed.r2dbc.multitenant.onboarding.tenant.TenantId
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingResponse
import exposed.r2dbc.multitenant.onboarding.tenant.TenantRegistryRepository
import exposed.r2dbc.multitenant.onboarding.tenant.TenantStatus
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult
import java.util.UUID

class TenantOnboardingControllerTest(
    @param:Autowired private val client: WebTestClient,
    @param:Autowired private val registryRepository: TenantRegistryRepository,
    @param:Autowired private val runtimeRegistry: TenantConnectionFactoryRegistry,
    @param:Autowired private val failureSwitch: ProvisioningFailureSwitch,
): AbstractTenantOnboardingTest() {

    @AfterEach
    fun clearFailureSwitch() {
        failureSwitch.failAt.set(null)
    }

    @Test
    fun `onboards tenant and routes actor request to provisioned database`() = runSuspendIO {
        val tenantId = nextTenantId()

        val response = onboard(tenantId)

        response.tenantId shouldBeEqualTo tenantId.value
        response.displayName shouldBeEqualTo "Tenant ${tenantId.value}"
        response.status shouldBeEqualTo TenantStatus.ACTIVE

        val actors = client
            .get()
            .uri("/actors")
            .header(TenantHeaders.TENANT_HEADER, tenantId.value)
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBodyList<ActorRecord>()
            .returnResult().responseBody
            .shouldNotBeNull()

        actors shouldHaveSize 2
        actors.first().lastName shouldBeEqualTo tenantId.value.replaceFirstChar { it.titlecase() }
    }

    @Test
    fun `duplicate tenant onboarding returns conflict`() = runSuspendIO {
        val tenantId = nextTenantId()
        onboard(tenantId)

        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to tenantId.value, "displayName" to "Duplicate"))
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @ParameterizedTest
    @EnumSource(ProvisioningFailurePoint::class)
    fun `provisioning failure removes registry row and runtime resources`(
        point: ProvisioningFailurePoint,
    ) = runSuspendIO {
        val tenantId = nextTenantId()
        failureSwitch.failAt.set(point)

        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to tenantId.value, "displayName" to "Tenant ${tenantId.value}"))
            .exchange()
            .expectStatus().is5xxServerError

        registryRepository.find(tenantId).shouldBeNull()
        runtimeRegistry.contains(tenantId).shouldBeFalse()
    }

    @Test
    fun `missing admin token is unauthorized`() = runSuspendIO {
        client
            .post()
            .uri("/api/tenants")
            .bodyValue(mapOf("tenantId" to nextTenantId().value, "displayName" to "Missing Token"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `invalid onboarding request returns bad request`() = runSuspendIO {
        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to "Bad Tenant", "displayName" to "Invalid Tenant"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `too long display name returns bad request before registry write`() = runSuspendIO {
        val tenantId = nextTenantId()

        client
            .post()
            .uri("/api/tenants")
            .header(TenantHeaders.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
            .bodyValue(mapOf("tenantId" to tenantId.value, "displayName" to "x".repeat(129)))
            .exchange()
            .expectStatus().isBadRequest

        registryRepository.find(tenantId).shouldBeNull()
    }

    @Test
    fun `unknown tenant actor request returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TenantHeaders.TENANT_HEADER, nextTenantId().value)
            .exchange()
            .expectStatus().isBadRequest
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
