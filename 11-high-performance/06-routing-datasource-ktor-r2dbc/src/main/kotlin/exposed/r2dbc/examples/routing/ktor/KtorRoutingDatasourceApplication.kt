package exposed.r2dbc.examples.routing.ktor

import exposed.r2dbc.examples.routing.ktor.config.KtorRoutingDatasourceResources
import exposed.r2dbc.examples.routing.ktor.config.installKtorRoutingDatasourcePlugins
import exposed.r2dbc.examples.routing.ktor.domain.RoutingMarkerRepository
import exposed.r2dbc.examples.routing.ktor.routes.routingMarkerRoutes
import exposed.r2dbc.examples.routing.ktor.routing.RoutingRequestPlugin
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::ktorRoutingDatasourceModule).start(wait = true)
}

/**
 * Installs the Ktor routing datasource workshop module.
 */
fun Application.ktorRoutingDatasourceModule(
    resources: KtorRoutingDatasourceResources = KtorRoutingDatasourceResources.create(),
) {
    installKtorRoutingDatasourcePlugins()
    install(RoutingRequestPlugin)

    val repository = RoutingMarkerRepository(
        registry = resources.registry,
        awaitReady = resources::awaitInitialized,
    )
    routingMarkerRoutes(repository)

    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }
}
