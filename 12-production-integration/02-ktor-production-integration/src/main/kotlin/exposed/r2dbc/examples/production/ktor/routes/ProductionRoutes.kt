package exposed.r2dbc.examples.production.ktor.routes

import exposed.r2dbc.examples.production.ktor.UserSession
import exposed.r2dbc.examples.production.ktor.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.ktor.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.OutboxEventView
import exposed.r2dbc.examples.production.ktor.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.ktor.config.ProductionJson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString

internal fun Application.productionRoutes(repository: KtorProductionRepository) {
    routing {
        post("/production/accounts") {
            val account = repository.registerAccount(call.receive<RegisterAccountRequest>())
            call.sessions.set(UserSession(account.id))
            call.respond(account)
        }

        authenticate("auth-session") {
            post("/production/work-items") {
                call.respond(repository.createWorkItem(call.receive<CreateWorkItemRequest>()))
            }

            webSocket("/production/realtime") {
                val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0L
                repository.replayEvents(after).forEach { event ->
                    send(Frame.Text(ProductionJson.encodeToString<OutboxEventView>(event)))
                }
            }

            post("/production/outbound") {
                call.respond(repository.enqueueOutbound(call.receive<EnqueueOutboundRequest>()))
            }
        }

        get("/production/readiness") {
            call.respond(repository.readiness())
        }
    }
}
