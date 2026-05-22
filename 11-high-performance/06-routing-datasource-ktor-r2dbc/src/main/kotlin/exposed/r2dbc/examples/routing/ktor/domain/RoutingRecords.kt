package exposed.r2dbc.examples.routing.ktor.domain

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Current routing target marker response.
 */
@Serializable
data class RoutingMarkerResponse(
    val tenant: String,
    val readOnly: Boolean,
    val routingKey: String,
    val marker: String?,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Marker update request.
 */
@Serializable
data class UpdateMarkerRequest(
    val marker: String,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Stable JSON error body.
 */
@Serializable
data class StructuredError(
    val code: String,
    val message: String,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
