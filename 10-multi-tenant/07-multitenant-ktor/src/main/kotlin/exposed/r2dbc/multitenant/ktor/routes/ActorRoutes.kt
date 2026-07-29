package exposed.r2dbc.multitenant.ktor.routes

import exposed.r2dbc.multitenant.ktor.domain.model.CreateActorRequest
import exposed.r2dbc.multitenant.ktor.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.ktor.tenant.currentTenant
import exposed.r2dbc.multitenant.ktor.tenant.suspendTransactionWithTenant
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

/**
 * tenant-aware actor route를 설치한다.
 */
fun Application.actorRoutes(
    database: R2dbcDatabase,
    repository: ActorR2dbcRepository,
) {
    routing {
        get("/actors") {
            val tenant = call.currentTenant()
            val actors = suspendTransactionWithTenant(tenant = tenant, db = database, readOnly = true) {
                repository.findAll().toList()
            }
            call.respond(actors)
        }

        get("/actors/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("id must be a positive integer")
            require(id > 0) { "id must be positive" }
            val tenant = call.currentTenant()
            val actor = suspendTransactionWithTenant(tenant = tenant, db = database, readOnly = true) {
                repository.findOne(id)
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(actor)
        }

        post("/actors") {
            val tenant = call.currentTenant()
            val request = call.receive<CreateActorRequest>()
            val actor = suspendTransactionWithTenant(tenant = tenant, db = database) {
                repository.save(request)
            }
            call.respond(HttpStatusCode.Created, actor)
        }
    }
}
