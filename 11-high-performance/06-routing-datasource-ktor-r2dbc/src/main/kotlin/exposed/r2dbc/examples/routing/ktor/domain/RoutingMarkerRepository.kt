package exposed.r2dbc.examples.routing.ktor.domain

import exposed.r2dbc.examples.routing.ktor.routing.RoutingDatabaseRegistry
import exposed.r2dbc.examples.routing.ktor.routing.RoutingRequest
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Reads and writes marker rows from the explicitly selected R2DBC target.
 */
class RoutingMarkerRepository(
    private val registry: RoutingDatabaseRegistry,
    private val awaitReady: suspend () -> Unit,
) {
    suspend fun findMarker(route: RoutingRequest): RoutingMarkerResponse {
        awaitReady()
        return route.toResponse(findMarkerValue(route))
    }

    suspend fun updateMarker(route: RoutingRequest, request: UpdateMarkerRequest): RoutingMarkerResponse {
        awaitReady()
        val writeRoute = route.asReadWrite()
        resetMarker(writeRoute, request.marker.requireNotBlank("marker"))
        return writeRoute.toResponse(findMarkerValue(writeRoute))
    }

    suspend fun resetMarker(route: RoutingRequest, marker: String) {
        suspendTransaction(db = registry.database(route)) {
            SchemaUtils.create(RoutingMarkerTable)
            RoutingMarkerTable.deleteAll()
            RoutingMarkerTable.insert {
                it[RoutingMarkerTable.marker] = marker
            }
        }
    }

    private suspend fun findMarkerValue(route: RoutingRequest): String? =
        suspendTransaction(db = registry.database(route), readOnly = true) {
            // Invariant: resetMarker replaces the marker table with one row per routing target.
            RoutingMarkerTable
                .selectAll()
                .singleOrNull()
                ?.getOrNull(RoutingMarkerTable.marker)
        }

    private fun RoutingRequest.toResponse(marker: String?): RoutingMarkerResponse =
        RoutingMarkerResponse(
            tenant = tenant.id,
            readOnly = readOnly,
            routingKey = key,
            marker = marker,
        )
}
