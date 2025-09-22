package exposed.r2dbc.multitenant.webflux.controller

import exposed.r2dbc.multitenant.webflux.AbstractMultitenantTest
import exposed.r2dbc.multitenant.webflux.domain.dto.ActorDTO
import exposed.r2dbc.multitenant.webflux.tenant.TenantFilter
import exposed.r2dbc.multitenant.webflux.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.webflux.tenant.Tenants
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

class ActorControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractMultitenantTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest(name = "tenant={0}")
    @EnumSource(Tenants.Tenant::class)
    fun `get all actors by tenant`(tenant: Tenants.Tenant) = runSuspendIO {
        val actors = client
            .get()
            .uri("/actors")
            .header(TenantFilter.TENANT_HEADER, tenant.id)
            .exchange()
            .expectStatus().isOk
            .returnResult<ActorDTO>().responseBody
            .asFlow().toList()

        actors.forEach {
            log.debug { "Tenant: ${tenant.id}, Actor: $it" }
        }
        actors shouldHaveSize 9

        val expectedFirstName = mapOf(
            Tenants.Tenant.KOREAN to "조니",
            Tenants.Tenant.ENGLISH to "Johnny"
        )
        actors.any { it.firstName == expectedFirstName[tenant] }.shouldBeTrue()
    }

    @ParameterizedTest(name = "tenant={0}")
    @EnumSource(Tenants.Tenant::class)
    fun `get actor by id with tenant`(tenant: Tenants.Tenant) = runSuspendIO {
        val actor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, tenant.id)
            .exchange()
            .expectStatus().isOk
            .returnResult<ActorDTO>().responseBody
            .awaitSingle()

        log.debug { "Tenant: ${tenant.id}, Actor: $actor" }

        val expectedFirstName = mapOf(
            Tenants.Tenant.KOREAN to "브래드",
            Tenants.Tenant.ENGLISH to "Brad"
        )
        actor.firstName shouldBeEqualTo expectedFirstName[tenant]
    }
}
