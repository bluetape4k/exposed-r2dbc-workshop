package exposed.r2dbc.multitenant.ktor

import exposed.r2dbc.multitenant.ktor.config.KtorMultitenantDatabase
import exposed.r2dbc.multitenant.ktor.config.KtorMultitenantJson
import exposed.r2dbc.multitenant.ktor.domain.model.ActorRecord
import exposed.r2dbc.multitenant.ktor.domain.model.CreateActorRequest
import exposed.r2dbc.multitenant.ktor.domain.model.StructuredError
import exposed.r2dbc.multitenant.ktor.tenant.TenantHeader
import exposed.r2dbc.multitenant.ktor.tenant.Tenants.Tenant
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorMultitenantApplicationTest {

    @Test
    fun `get all actors by tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("read-all"))
        }
        val client = createJsonClient()

        Tenant.entries.forEach { tenant ->
            val actors = client.get("/actors") {
                header(TenantHeader, tenant.id)
            }.body<List<ActorRecord>>()

            actors shouldHaveSize 9
            val expectedFirstName = when (tenant) {
                Tenant.KOREAN -> "조니"
                Tenant.ENGLISH -> "Johnny"
            }
            actors.any { it.firstName == expectedFirstName }.shouldBeTrue()
        }
    }

    @Test
    fun `get actor by id keeps tenant data isolated`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("read-one"))
        }
        val client = createJsonClient()

        val koreanActor = client.get("/actors/2") {
            header(TenantHeader, Tenant.KOREAN.id)
        }.body<ActorRecord>()
        val englishActor = client.get("/actors/2") {
            header(TenantHeader, Tenant.ENGLISH.id)
        }.body<ActorRecord>()

        koreanActor.id shouldBeEqualTo englishActor.id
        koreanActor.firstName shouldBeEqualTo "브래드"
        koreanActor.lastName shouldBeEqualTo "피트"
        englishActor.firstName shouldBeEqualTo "Brad"
        englishActor.lastName shouldBeEqualTo "Pitt"
    }

    @Test
    fun `lowercase tenant header resolves tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("lowercase"))
        }
        val client = createJsonClient()

        val actors = client.get("/actors") {
            header("x-tenant-id", Tenant.KOREAN.id)
        }.body<List<ActorRecord>>()

        actors shouldHaveSize 9
    }

    @Test
    fun `invalid tenant headers return structured 400`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("invalid"))
        }
        val client = createJsonClient()

        val missing = client.get("/actors")
        missing.status shouldBeEqualTo HttpStatusCode.BadRequest
        missing.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val empty = client.get("/actors") {
            header(TenantHeader, "")
        }
        empty.status shouldBeEqualTo HttpStatusCode.BadRequest
        empty.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val whitespace = client.get("/actors") {
            header(TenantHeader, "   ")
        }
        whitespace.status shouldBeEqualTo HttpStatusCode.BadRequest
        whitespace.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"

        val unknown = client.get("/actors") {
            header(TenantHeader, "unknown-tenant")
        }
        unknown.status shouldBeEqualTo HttpStatusCode.BadRequest
        val error = unknown.body<StructuredError>()
        error.code shouldBeEqualTo "INVALID_TENANT"
        error.message shouldContain "Unknown tenant id"
    }

    @Test
    fun `conflicting duplicate tenant headers are rejected`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("duplicate"))
        }
        val client = createJsonClient()

        val same = client.get("/actors") {
            headers {
                append(TenantHeader, "korean")
                append(TenantHeader, " korean ")
            }
        }
        same.status shouldBeEqualTo HttpStatusCode.OK

        val conflicting = client.get("/actors") {
            headers {
                append(TenantHeader, "korean")
                append(TenantHeader, "english")
            }
        }
        conflicting.status shouldBeEqualTo HttpStatusCode.BadRequest
        conflicting.body<StructuredError>().code shouldBeEqualTo "INVALID_TENANT"
    }

    @Test
    fun `write in one tenant is not visible from another tenant`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("write-isolation"))
        }
        val client = createJsonClient()

        val created = client.post("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
            contentType(ContentType.Application.Json)
            setBody(CreateActorRequest("테스트", "배우", "2000-01-01"))
        }.body<ActorRecord>()

        created.id shouldBeGreaterThan 9L
        client.get("/actors/${created.id}") {
            header(TenantHeader, Tenant.ENGLISH.id)
        }.status shouldBeEqualTo HttpStatusCode.NotFound

        val koreanActors = client.get("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
        }.body<List<ActorRecord>>()
        koreanActors.map { it.firstName } shouldContain "테스트"
    }

    @Test
    fun `rapid tenant alternation reuses one pool without schema leakage`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("pool-one", maxPoolSize = 1))
        }
        val client = createJsonClient()

        val observed = (1..20).map { index ->
            val tenant = if (index % 2 == 0) Tenant.ENGLISH else Tenant.KOREAN
            client.get("/actors/2") {
                header(TenantHeader, tenant.id)
            }.body<ActorRecord>().firstName
        }

        observed.filterIndexed { index, _ -> index % 2 == 0 }.forEach {
            it shouldBeEqualTo "브래드"
        }
        observed.filterIndexed { index, _ -> index % 2 == 1 }.forEach {
            it shouldBeEqualTo "Brad"
        }
    }

    @Test
    fun `overlapping tenant requests do not leak call attributes`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("concurrent", maxPoolSize = 1))
        }
        val client = createJsonClient()

        val firstNames = coroutineScope {
            (1..16).map { index ->
                async {
                    val tenant = if (index % 2 == 0) Tenant.ENGLISH else Tenant.KOREAN
                    client.get("/actors/2") {
                        header(TenantHeader, tenant.id)
                    }.body<ActorRecord>().firstName
                }
            }.awaitAll()
        }

        firstNames shouldContain "브래드"
        firstNames shouldContain "Brad"
    }

    @Test
    fun `invalid actor id returns structured request error`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("invalid-id"))
        }
        val client = createJsonClient()

        val nonNumeric = client.get("/actors/not-a-number") {
            header(TenantHeader, Tenant.KOREAN.id)
        }
        nonNumeric.status shouldBeEqualTo HttpStatusCode.BadRequest
        nonNumeric.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val negative = client.get("/actors/-1") {
            header(TenantHeader, Tenant.KOREAN.id)
        }
        negative.status shouldBeEqualTo HttpStatusCode.BadRequest
        negative.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    @Test
    fun `malformed create actor json returns structured json error`() = testApplication {
        application {
            ktorMultitenantModule(newDatabase("malformed-json"))
        }
        val client = createJsonClient()

        val response = client.post("/actors") {
            header(TenantHeader, Tenant.KOREAN.id)
            contentType(ContentType.Application.Json)
            setBody("""{"firstName":""")
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        response.body<StructuredError>().code shouldBeEqualTo "INVALID_JSON"
    }

    private fun newDatabase(slug: String, maxPoolSize: Int = 8): KtorMultitenantDatabase =
        KtorMultitenantDatabase.create(
            databaseName = "ktor_multitenant_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            maxPoolSize = maxPoolSize,
        )

    private fun ApplicationTestBuilder.createJsonClient(): HttpClient =
        createClient {
            install(ContentNegotiation) {
                json(KtorMultitenantJson)
            }
        }
}
