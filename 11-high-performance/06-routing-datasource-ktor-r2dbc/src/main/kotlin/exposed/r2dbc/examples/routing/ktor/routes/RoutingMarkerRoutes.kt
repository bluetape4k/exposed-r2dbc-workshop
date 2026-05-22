package exposed.r2dbc.examples.routing.ktor.routes

import exposed.r2dbc.examples.routing.ktor.domain.RoutingMarkerRepository
import exposed.r2dbc.examples.routing.ktor.domain.UpdateMarkerRequest
import exposed.r2dbc.examples.routing.ktor.routing.InvalidRoutingRequestException
import exposed.r2dbc.examples.routing.ktor.routing.routingRequest
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.routing

/**
 * Installs routing datasource marker endpoints.
 */
fun Application.routingMarkerRoutes(repository: RoutingMarkerRepository) {
    routing {
        get("/routing/marker") {
            call.respond(repository.findMarker(call.routingRequest()))
        }

        get("/routing/marker/readonly") {
            call.respond(repository.findMarker(call.routingRequest()))
        }

        patch("/routing/marker") {
            val route = call.routingRequest()
            if (route.readOnly) {
                throw InvalidRoutingRequestException("PATCH /routing/marker cannot use read-only routing")
            }
            call.respond(repository.updateMarker(route, call.receive<UpdateMarkerRequest>()))
        }
    }
}
