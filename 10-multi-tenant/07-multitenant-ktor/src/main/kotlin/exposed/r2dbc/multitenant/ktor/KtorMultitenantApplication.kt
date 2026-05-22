package exposed.r2dbc.multitenant.ktor

import exposed.r2dbc.multitenant.ktor.config.KtorMultitenantDatabase
import exposed.r2dbc.multitenant.ktor.config.installKtorMultitenantPlugins
import exposed.r2dbc.multitenant.ktor.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.ktor.routes.actorRoutes
import exposed.r2dbc.multitenant.ktor.tenant.DataInitializer
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.runBlocking

/**
 * Installs the Ktor schema-per-tenant R2DBC workshop example.
 */
fun Application.ktorMultitenantModule(
    database: KtorMultitenantDatabase = KtorMultitenantDatabase.create(),
    actorRepository: ActorR2dbcRepository = ActorR2dbcRepository(),
) {
    monitor.subscribe(ApplicationStopped) {
        database.close()
    }

    installKtorMultitenantPlugins()

    // Startup intentionally blocks until tenant schemas are ready, so bad schema setup fails fast.
    runBlocking {
        DataInitializer(database.database).initializeAll()
    }

    actorRoutes(database.database, actorRepository)
}
