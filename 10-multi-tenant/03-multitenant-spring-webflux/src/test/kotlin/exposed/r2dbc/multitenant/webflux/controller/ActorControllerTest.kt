package exposed.r2dbc.multitenant.webflux.controller

import exposed.r2dbc.multitenant.webflux.AbstractMultitenantTest
import exposed.r2dbc.multitenant.webflux.domain.model.ActorRecord
import exposed.r2dbc.multitenant.webflux.tenant.TenantFilter
import exposed.r2dbc.multitenant.webflux.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.webflux.tenant.Tenants
import exposed.r2dbc.multitenant.webflux.tenant.Tenants.Tenant
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.reactive.awaitSingle
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult

/**
 * 멀티테넌트 Actor API에서 tenant 헤더 기반 분기를 검증합니다.
 */
class ActorControllerTest(
    @param:Autowired private val client: WebTestClient,
): AbstractMultitenantTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest(name = "tenant={0}")
    @EnumSource(Tenant::class)
    fun `get all actors by tenant`(tenant: Tenant) = runSuspendIO {
        val actors = client
            .get()
            .uri("/actors")
            .header(TenantFilter.TENANT_HEADER, tenant.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBodyList<ActorRecord>()
            .returnResult().responseBody
            .shouldNotBeNull()

        actors.forEach {
            log.debug { "Tenant: ${tenant.id}, Actor: $it" }
        }
        actors shouldHaveSize 9

        val expectedFirstName = mapOf(
            Tenant.KOREAN to "조니",
            Tenant.ENGLISH to "Johnny"
        )
        actors.any { it.firstName == expectedFirstName[tenant] }.shouldBeTrue()
    }

    @ParameterizedTest(name = "tenant={0}")
    @EnumSource(Tenant::class)
    fun `get actor by id with tenant`(tenant: Tenant) = runSuspendIO {
        val actor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, tenant.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        log.debug { "Tenant: ${tenant.id}, Actor: $actor" }

        val expectedFirstName = mapOf(
            Tenant.KOREAN to "브래드",
            Tenant.ENGLISH to "Brad"
        )
        actor.firstName shouldBeEqualTo expectedFirstName[tenant]
    }

    @ParameterizedTest(name = "tenant={0}")
    @EnumSource(Tenant::class)
    fun `헤더 이름 대소문자와 무관하게 동일 tenant 조회`(tenant: Tenant) = runSuspendIO {
        val actors = client
            .get()
            .uri("/actors")
            .header("x-tenant-id", tenant.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBodyList<ActorRecord>()
            .returnResult().responseBody
            .shouldNotBeNull()

        actors shouldHaveSize 9
    }

    @org.junit.jupiter.api.Test
    fun `tenant 헤더가 없으면 기본 tenant 조회와 동일하다`() = runSuspendIO {
        val defaultTenant = Tenants.DEFAULT_TENANT
        val actorWithHeader = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, defaultTenant.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()
        val actorWithoutHeader = client
            .get()
            .uri("/actors/2")
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        actorWithoutHeader.firstName shouldBeEqualTo actorWithHeader.firstName
    }
}
