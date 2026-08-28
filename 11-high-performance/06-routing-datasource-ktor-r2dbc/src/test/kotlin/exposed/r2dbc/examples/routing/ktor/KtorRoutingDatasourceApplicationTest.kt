package exposed.r2dbc.examples.routing.ktor

import exposed.r2dbc.examples.routing.ktor.config.KtorRoutingDatasourceJson
import exposed.r2dbc.examples.routing.ktor.config.KtorRoutingDatasourceResources
import exposed.r2dbc.examples.routing.ktor.domain.RoutingMarkerResponse
import exposed.r2dbc.examples.routing.ktor.domain.StructuredError
import exposed.r2dbc.examples.routing.ktor.domain.UpdateMarkerRequest
import exposed.r2dbc.examples.routing.ktor.routing.READ_ONLY_HEADER
import exposed.r2dbc.examples.routing.ktor.routing.TENANT_HEADER
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test
import java.util.UUID

class KtorRoutingDatasourceApplicationTest {

    @Test
    fun `default route reads default read-write marker`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("default-rw"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)

        val response = client.get("/routing/marker").body<RoutingMarkerResponse>()

        response.tenant shouldBeEqualTo "default"
        response.readOnly shouldBeEqualTo false
        response.routingKey shouldBeEqualTo "default:rw"
        response.marker shouldBeEqualTo "default-rw"
    }

    @Test
    fun `tenant header selects tenant read-write marker`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("acme-rw"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)

        val response = client.get("/routing/marker") {
            header(TENANT_HEADER, "acme")
        }.body<RoutingMarkerResponse>()

        response.tenant shouldBeEqualTo "acme"
        response.readOnly shouldBeEqualTo false
        response.routingKey shouldBeEqualTo "acme:rw"
        response.marker shouldBeEqualTo "acme-rw"
    }

    @Test
    fun `readonly path and header select read-only target`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("readonly"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)

        val pathResponse = client.get("/routing/marker/readonly") {
            header(TENANT_HEADER, "acme")
        }.body<RoutingMarkerResponse>()
        val headerResponse = client.get("/routing/marker") {
            header(TENANT_HEADER, "acme")
            header(READ_ONLY_HEADER, "true")
        }.body<RoutingMarkerResponse>()

        pathResponse.marker shouldBeEqualTo "acme-ro"
        headerResponse.marker shouldBeEqualTo "acme-ro"
        pathResponse.routingKey shouldBeEqualTo "acme:ro"
        headerResponse.routingKey shouldBeEqualTo "acme:ro"
    }

    @Test
    fun `patch updates only selected tenant read-write target`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("patch"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)

        val updated = client.patch("/routing/marker") {
            header(TENANT_HEADER, "acme")
            contentType(ContentType.Application.Json)
            setBody(UpdateMarkerRequest("acme-rw-updated"))
        }.body<RoutingMarkerResponse>()
        val readWrite = client.get("/routing/marker") {
            header(TENANT_HEADER, "acme")
        }.body<RoutingMarkerResponse>()
        val readOnly = client.get("/routing/marker/readonly") {
            header(TENANT_HEADER, "acme")
        }.body<RoutingMarkerResponse>()

        updated.marker shouldBeEqualTo "acme-rw-updated"
        readWrite.marker shouldBeEqualTo "acme-rw-updated"
        readOnly.marker shouldBeEqualTo "acme-ro"
    }

    @Test
    fun `invalid routing input returns structured errors`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("errors"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)

        val blankTenant = client.get("/routing/marker") {
            header(TENANT_HEADER, " ")
        }.body<RoutingMarkerResponse>()
        blankTenant.marker shouldBeEqualTo "default-rw"

        val unknownTenant = client.get("/routing/marker") {
            header(TENANT_HEADER, "unknown")
        }
        unknownTenant.status shouldBeEqualTo HttpStatusCode.BadRequest
        unknownTenant.body<StructuredError>().code shouldBeEqualTo "INVALID_ROUTING_REQUEST"

        val invalidReadOnly = client.get("/routing/marker") {
            header(READ_ONLY_HEADER, "sometimes")
        }
        invalidReadOnly.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalidReadOnly.body<StructuredError>().message shouldBeEqualTo "$READ_ONLY_HEADER must be true or false"

        val readOnlyPatch = client.patch("/routing/marker") {
            header(READ_ONLY_HEADER, "true")
            contentType(ContentType.Application.Json)
            setBody(UpdateMarkerRequest("should-not-write"))
        }
        readOnlyPatch.status shouldBeEqualTo HttpStatusCode.BadRequest
        readOnlyPatch.body<StructuredError>().message shouldBeEqualTo "PATCH /routing/marker cannot use read-only routing"

        val blankMarker = client.patch("/routing/marker") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMarkerRequest(""))
        }
        blankMarker.status shouldBeEqualTo HttpStatusCode.BadRequest
        blankMarker.body<StructuredError>().code shouldBeEqualTo "INVALID_ROUTING_REQUEST"
    }

    @Test
    fun `concurrent alternating requests keep per-call routing`() = testApplication {
        application {
            ktorRoutingDatasourceModule(newResources("concurrent"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorRoutingDatasourceJson)
        val tenants = listOf("default", "acme", "default", "acme", "default", "acme")

        val responses = coroutineScope {
            tenants.map { tenant ->
                async {
                    tenant to client.get("/routing/marker") {
                        header(TENANT_HEADER, tenant)
                    }.body<RoutingMarkerResponse>()
                }
            }.awaitAll()
        }

        responses shouldHaveSize tenants.size
        responses.forEach { (tenant, response) ->
            response.tenant shouldBeEqualTo tenant
            response.readOnly shouldBeEqualTo false
            response.routingKey shouldBeEqualTo "$tenant:rw"
            response.marker shouldBeEqualTo "$tenant-rw"
        }
    }

    private fun newResources(name: String): KtorRoutingDatasourceResources =
        KtorRoutingDatasourceResources.create(
            databasePrefix = "routing_${name}_${UUID.randomUUID().toString().replace("-", "")}",
        )

}
