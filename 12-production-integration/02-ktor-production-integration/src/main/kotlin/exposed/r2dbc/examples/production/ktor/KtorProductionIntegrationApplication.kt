package exposed.r2dbc.examples.production.ktor

import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import exposed.r2dbc.examples.production.ktor.config.installProductionKtorPlugins
import exposed.r2dbc.examples.production.ktor.routes.productionRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.cookie
import io.ktor.server.websocket.WebSockets
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.security.SecureRandom

/**
 * Installs the Ktor production integration example.
 */
fun Application.productionIntegrationModule(
    repository: KtorProductionRepository = KtorProductionRepository(defaultProductionDatabase()),
) {
    installProductionKtorPlugins()
    install(Sessions) {
        cookie<UserSession>("production_session") {
            transform(SessionTransportTransformerMessageAuthentication(SessionSigningKey))
        }
    }
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session -> session }
            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StructuredError("UNAUTHORIZED", "Session is required")
                )
            }
        }
    }
    install(WebSockets)

    productionRoutes(repository)
}

/**
 * Creates the default H2 R2DBC database for the standalone Ktor example.
 */
fun defaultProductionDatabase(): R2dbcDatabase =
    R2dbcDatabase.connect("r2dbc:h2:mem:///ktor-production-integration;DB_CLOSE_DELAY=-1;USER=sa;")

private val SessionSigningKey: ByteArray = ByteArray(32).also {
    SecureRandom().nextBytes(it)
}
