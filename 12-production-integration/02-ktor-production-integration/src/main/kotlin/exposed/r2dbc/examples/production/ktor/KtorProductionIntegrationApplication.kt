package exposed.r2dbc.examples.production.ktor

import exposed.r2dbc.examples.production.ktor.app.KtorProductionRepository
import exposed.r2dbc.examples.production.ktor.app.KtorRealtimeHub
import exposed.r2dbc.examples.production.ktor.app.OutboundDelivery
import exposed.r2dbc.examples.production.ktor.app.RealtimeDelivery
import exposed.r2dbc.examples.production.ktor.app.StructuredError
import exposed.r2dbc.examples.production.ktor.app.AuthPrincipal
import exposed.r2dbc.examples.production.ktor.config.installProductionKtorPlugins
import exposed.r2dbc.examples.production.ktor.outbound.KtorOutboundDispatcher
import exposed.r2dbc.examples.production.ktor.routes.productionRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.session
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.cookie
import io.ktor.server.websocket.WebSockets
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.security.SecureRandom

/**
 * Ktor production integration 예제 module을 설치합니다.
 */
fun Application.productionIntegrationModule(
    repository: KtorProductionRepository = KtorProductionRepository(defaultProductionDatabase()),
    realtimeHub: KtorRealtimeHub = KtorRealtimeHub(),
    realtimeDelivery: RealtimeDelivery = realtimeHub,
    outboundDelivery: OutboundDelivery = KtorOutboundDispatcher(),
) {
    installProductionKtorPlugins()
    monitor.subscribe(ApplicationStopped) {
        if (outboundDelivery is AutoCloseable) {
            outboundDelivery.close()
        }
    }
    install(Sessions) {
        cookie<UserSession>("production_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 60 * 60
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(SessionSigningKey))
        }
    }
    install(Authentication) {
        basic("auth-basic") {
            realm = "production-integration"
            validate { credentials ->
                repository.authenticate(credentials.name, credentials.password)
                    ?.let { AuthPrincipal(it.username, it.displayName, it.permission, it.roles) }
            }
        }
        session<UserSession>("auth-session") {
            validate { session ->
                repository.findSessionByToken(session.token)
                    ?.let { session }
            }
            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StructuredError(
                        code = "UNAUTHORIZED",
                        message = "Session is required",
                        requestId = call.callId.orEmpty(),
                    )
                )
            }
        }
    }
    install(WebSockets)

    productionRoutes(repository, realtimeHub, realtimeDelivery, outboundDelivery)
}

/**
 * 독립 실행 Ktor 예제에서 사용할 기본 H2 R2DBC 데이터베이스를 생성합니다.
 */
fun defaultProductionDatabase(): R2dbcDatabase =
    R2dbcDatabase.connect("r2dbc:h2:mem:///ktor-production-integration;DB_CLOSE_DELAY=-1;USER=sa;")

private val SessionSigningKey: ByteArray = ByteArray(32).also {
    SecureRandom().nextBytes(it)
}
