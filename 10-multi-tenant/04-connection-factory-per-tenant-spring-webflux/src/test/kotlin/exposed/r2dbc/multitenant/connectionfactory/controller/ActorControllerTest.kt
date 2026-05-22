package exposed.r2dbc.multitenant.connectionfactory.controller

import exposed.r2dbc.multitenant.connectionfactory.AbstractMultitenantTest
import exposed.r2dbc.multitenant.connectionfactory.domain.model.ActorRecord
import exposed.r2dbc.multitenant.connectionfactory.tenant.TenantFilter
import exposed.r2dbc.multitenant.connectionfactory.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.connectionfactory.tenant.Tenants.Tenant
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult

/**
 * Verifies tenant routing through the actor HTTP API.
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

    @Test
    fun `tenant connection factories isolate actor data for the same id`() = runSuspendIO {
        val koreanActor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        val englishActor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, Tenant.ENGLISH.id)
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        koreanActor.id shouldBeEqualTo englishActor.id
        koreanActor.firstName shouldBeEqualTo "브래드"
        koreanActor.lastName shouldBeEqualTo "피트"
        englishActor.firstName shouldBeEqualTo "Brad"
        englishActor.lastName shouldBeEqualTo "Pitt"
    }

    @Test
    fun `concurrent tenant requests never cross route`() = runSuspendIO {
        val responses = coroutineScope {
            (1..50)
                .map { index ->
                    async {
                        val tenant = if (index % 2 == 0) Tenant.KOREAN else Tenant.ENGLISH
                        val actor = client
                            .get()
                            .uri("/actors/2")
                            .header(TENANT_HEADER, tenant.id)
                            .exchange()
                            .expectStatus().is2xxSuccessful
                            .returnResult<ActorRecord>().responseBody
                            .awaitSingle()

                        tenant to actor
                    }
                }
                .awaitAll()
        }

        responses shouldHaveSize 50
        responses.forEach { (tenant, actor) ->
            when (tenant) {
                Tenant.KOREAN -> {
                    actor.firstName shouldBeEqualTo "브래드"
                    actor.lastName shouldBeEqualTo "피트"
                }
                Tenant.ENGLISH -> {
                    actor.firstName shouldBeEqualTo "Brad"
                    actor.lastName shouldBeEqualTo "Pitt"
                }
            }
        }
    }

    @Test
    fun `missing tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `blank tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `unknown tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "unknown-tenant")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `malformed tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "bad tenant")
            .exchange()
            .expectStatus().isBadRequest
    }
}
