package exposed.r2dbc.examples.routing.ktor.domain

import org.jetbrains.exposed.v1.core.Table

/**
 * Single-row table that identifies the selected routing target.
 */
object RoutingMarkerTable: Table("routing_marker") {
    val marker = varchar("marker", 100)
}
