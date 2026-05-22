package exposed.r2dbc.examples.production.ktor.routes

import exposed.r2dbc.examples.production.ktor.UserSession
import exposed.r2dbc.examples.production.ktor.app.AuthPrincipal
import exposed.r2dbc.examples.production.ktor.app.AuthProfileView
import exposed.r2dbc.examples.production.ktor.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.ktor.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.OutboxEventView
import exposed.r2dbc.examples.production.ktor.app.PermissionDeniedException
import exposed.r2dbc.examples.production.ktor.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.ktor.app.SessionsView
import exposed.r2dbc.examples.production.ktor.config.ProductionJson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString

internal fun Application.productionRoutes(repository: KtorProductionRepository) {
    routing {
        post("/production/accounts") {
            val account = repository.registerAccount(call.receive<RegisterAccountRequest>())
            call.respond(account)
        }

        authenticate("auth-basic") {
            get("/production/profile") {
                val principal = call.authPrincipal()
                call.respond(AuthProfileView(principal.username, principal.displayName, principal.roles))
            }

            get("/production/admin") {
                val principal = call.authPrincipal()
                if ("ADMIN" !in principal.roles) {
                    throw PermissionDeniedException("ADMIN")
                }
                call.respond(AuthProfileView(principal.username, principal.displayName, principal.roles))
            }

            post("/production/sessions") {
                val session = repository.createSession(call.authPrincipal().username)
                val rawToken = requireNotNull(session.token) {
                    "Created session must include raw token for cookie transport"
                }
                call.sessions.set(UserSession(rawToken))
                call.respond(session)
            }

            get("/production/sessions") {
                call.respond(SessionsView(repository.findSessions(call.authPrincipal().username)))
            }
        }

        authenticate("auth-session") {
            post("/production/work-items") {
                call.requireSessionPermission(repository, "work:create")
                call.respond(repository.createWorkItem(call.receive<CreateWorkItemRequest>()))
            }

            webSocket("/production/realtime") {
                call.requireSessionPermission(repository, "work:create")
                val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0L
                repository.replayEvents(after).forEach { event ->
                    send(Frame.Text(ProductionJson.encodeToString<OutboxEventView>(event)))
                }
            }

            post("/production/outbound") {
                call.requireSessionPermission(repository, "outbound:create")
                call.respond(repository.enqueueOutbound(call.receive<EnqueueOutboundRequest>()))
            }
        }

        get("/production/readiness") {
            call.respond(repository.readiness())
        }
    }
}

private fun ApplicationCall.authPrincipal(): AuthPrincipal =
    checkNotNull(principal<AuthPrincipal>()) {
        "Authenticated principal was not installed"
    }

private suspend fun ApplicationCall.requireSessionPermission(
    repository: KtorProductionRepository,
    permission: String,
) {
    val token = checkNotNull(sessions.get<UserSession>()) {
        "Authenticated session was not installed"
    }.token
    if (!repository.hasSessionPermission(token, permission)) {
        throw PermissionDeniedException(permission)
    }
}
