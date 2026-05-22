package exposed.r2dbc.multitenant.security.controller

import exposed.r2dbc.multitenant.security.AbstractMultitenantTest
import exposed.r2dbc.multitenant.security.domain.model.ActorRecord
import exposed.r2dbc.multitenant.security.security.ApiKeyAuthenticationWebFilter.Companion.API_KEY_HEADER
import exposed.r2dbc.multitenant.security.security.SessionTenantAuthenticationWebFilter.Companion.SESSION_HEADER
import exposed.r2dbc.multitenant.security.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
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
            .withTenantApiKey(tenant)
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
            .withTenantApiKey(tenant)
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
            .header(API_KEY_HEADER, apiKeyFor(tenant))
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
            .withTenantApiKey(Tenant.KOREAN)
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        val englishActor = client
            .get()
            .uri("/actors/2")
            .withTenantApiKey(Tenant.ENGLISH)
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
                            .withTenantApiKey(tenant)
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
            .header(API_KEY_HEADER, apiKeyFor(Tenant.KOREAN))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `blank tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "")
            .header(API_KEY_HEADER, apiKeyFor(Tenant.KOREAN))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `unknown tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "unknown-tenant")
            .header(API_KEY_HEADER, apiKeyFor(Tenant.KOREAN))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `malformed tenant header returns bad request`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "bad tenant")
            .header(API_KEY_HEADER, apiKeyFor(Tenant.KOREAN))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `missing authentication returns unauthorized`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `missing authentication with malformed tenant header returns unauthorized`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, "bad tenant")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `invalid api key returns unauthorized`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .header(API_KEY_HEADER, "invalid-key")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `invalid demo session returns unauthorized`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .header(SESSION_HEADER, "invalid-session")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `api key tenant mismatch returns forbidden`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.ENGLISH.id)
            .header(API_KEY_HEADER, apiKeyFor(Tenant.KOREAN))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `jwt tenant claim accesses matching tenant`() = runSuspendIO {
        val actor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, Tenant.ENGLISH.id)
            .headers { it.setBearerAuth("english-token") }
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        actor.firstName shouldBeEqualTo "Brad"
        actor.lastName shouldBeEqualTo "Pitt"
    }

    @Test
    fun `jwt tenant mismatch returns forbidden`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.ENGLISH.id)
            .headers { it.setBearerAuth("korean-token") }
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `jwt without tenant claim returns forbidden`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .headers { it.setBearerAuth("missing-tenant-token") }
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `jwt with unknown tenant claim returns forbidden`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .headers { it.setBearerAuth("unknown-token") }
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `demo session tenant accesses matching tenant`() = runSuspendIO {
        val actor = client
            .get()
            .uri("/actors/2")
            .header(TENANT_HEADER, Tenant.KOREAN.id)
            .header(SESSION_HEADER, "korean-session")
            .exchange()
            .expectStatus().is2xxSuccessful
            .returnResult<ActorRecord>().responseBody
            .awaitSingle()

        actor.firstName shouldBeEqualTo "브래드"
        actor.lastName shouldBeEqualTo "피트"
    }

    @Test
    fun `demo session tenant mismatch returns forbidden`() = runSuspendIO {
        client
            .get()
            .uri("/actors")
            .header(TENANT_HEADER, Tenant.ENGLISH.id)
            .header(SESSION_HEADER, "korean-session")
            .exchange()
            .expectStatus().isForbidden
    }

    private fun WebTestClient.RequestHeadersSpec<*>.withTenantApiKey(
        tenant: Tenant,
    ): WebTestClient.RequestHeadersSpec<*> =
        header(TENANT_HEADER, tenant.id)
            .header(API_KEY_HEADER, apiKeyFor(tenant))

    private fun apiKeyFor(tenant: Tenant): String =
        when (tenant) {
            Tenant.KOREAN  -> "demo-korean-key"
            Tenant.ENGLISH -> "demo-english-key"
        }
}
