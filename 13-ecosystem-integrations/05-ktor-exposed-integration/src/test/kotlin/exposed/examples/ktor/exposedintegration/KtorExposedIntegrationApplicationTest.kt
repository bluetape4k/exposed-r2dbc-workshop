package exposed.examples.ktor.exposedintegration

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorExposedIntegrationApplicationTest {

    @Test
    fun `r2dbc transaction creates and lists notes`() = testApplication {
        val resources = KtorExposedIntegrationResources.create("crud")
        resources.repository.initialize()
        application {
            installKtorExposedIntegrationWorkshop(resources)
        }

        val client = jsonClient()
        val created = client.post("/api/notes") {
            contentType(ContentType.Application.Json)
            setBody(NoteRequest("r2dbc boundary", "suspend transaction owns the write"))
        }.body<NoteResponse>()

        created.title shouldBeEqualTo "r2dbc boundary"
        created.body shouldBeEqualTo "suspend transaction owns the write"

        val notes = client.get("/api/notes").body<List<NoteResponse>>()
        notes.map { it.id } shouldContain created.id

        resources.close()
    }

    @Test
    fun `health and readiness expose the r2dbc boundary`() = testApplication {
        val resources = KtorExposedIntegrationResources.create("readiness")
        resources.repository.initialize()
        application {
            installKtorExposedIntegrationWorkshop(resources)
        }

        val client = jsonClient()
        client.get("/healthz/exposed").body<HealthResponse>() shouldBeEqualTo
            HealthResponse.up(mapOf("exposed" to "UP"))
        client.get("/readyz/exposed").body<HealthResponse>() shouldBeEqualTo
            HealthResponse.up(mapOf("r2dbc" to "UP"))

        resources.close()
    }

    @Test
    fun `readiness maps a closed r2dbc resource to a sanitized error`() = testApplication {
        val resources = KtorExposedIntegrationResources.create("readiness-failure")
        resources.repository.initialize()
        application {
            installKtorExposedIntegrationWorkshop(resources)
        }
        resources.close()

        val response = jsonClient().get("/readyz/exposed")
        response.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable
        response.bodyAsText() shouldContain "R2DBC_DATABASE_UNAVAILABLE"
        response.bodyAsText() shouldNotContain "r2dbc:h2:mem:"
    }

    @Test
    fun `pool bounds and cleanup are caller owned and idempotent`() = runTest {
        val resources = KtorExposedIntegrationResources.create("pool")
        try {
            resources.r2dbcPool.warmup().awaitSingle()
            val metrics = resources.r2dbcPool.metrics.orElseThrow()
            metrics.maxAllocatedSize shouldBeEqualTo 2
            metrics.allocatedSize() shouldBeEqualTo 1
            resources.r2dbcPool.isDisposed shouldBeEqualTo false
        } finally {
            resources.close()
            resources.close()
        }

        resources.r2dbcPool.isDisposed shouldBeEqualTo true
    }

    @Test
    fun `application stopped disposes caller owned pool`() = testApplication {
        val resources = KtorExposedIntegrationResources.create("application-stopped")
        application {
            installKtorExposedIntegrationWorkshop(resources)
        }
        startApplication()

        application.monitor.raise(ApplicationStopped, application)

        resources.r2dbcPool.isDisposed shouldBeEqualTo true
        resources.close()
    }

    @Test
    fun `validation and database errors are structured`() = testApplication {
        val resources = KtorExposedIntegrationResources.create("errors")
        resources.repository.initialize()
        application {
            installKtorExposedIntegrationWorkshop(resources)
        }

        val client = jsonClient()
        val invalid = client.post("/api/notes") {
            contentType(ContentType.Application.Json)
            setBody(NoteRequest(" ", "body"))
        }
        invalid.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalid.body<ErrorResponse>().code shouldBeEqualTo "INVALID_REQUEST"

        val failure = client.get("/api/failures/sql")
        failure.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable
        val failureText = failure.bodyAsText()
        failureText shouldContain "R2DBC_DATABASE_UNAVAILABLE"
        failureText shouldNotContain "top-secret"
        failureText shouldNotContain "select * from notes"

        resources.close()
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(Json { encodeDefaults = true })
            }
        }
}
