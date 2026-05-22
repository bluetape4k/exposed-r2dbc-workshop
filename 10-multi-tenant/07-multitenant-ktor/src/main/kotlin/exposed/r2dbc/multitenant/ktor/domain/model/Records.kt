package exposed.r2dbc.multitenant.ktor.domain.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Actor row returned by the Ktor multi-tenant example.
 */
@Serializable
data class ActorRecord(
    val id: Long = 0L,
    val firstName: String,
    val lastName: String,
    val birthday: String? = null,
): JavaSerializable {
    fun withId(id: Long) = copy(id = id)

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Request body for creating an actor in the current tenant schema.
 */
@Serializable
data class CreateActorRequest(
    val firstName: String,
    val lastName: String,
    val birthday: String? = null,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Movie row used by seed data and chapter comparison docs.
 */
@Serializable
data class MovieRecord(
    val id: Long = 0L,
    val name: String,
    val producerName: String,
    val releaseDate: String,
): JavaSerializable {
    fun withId(id: Long) = copy(id = id)

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Movie plus actor list used during seed data creation.
 *
 * This type stays internal to seed setup, so it does not need a JSON serializer.
 */
data class MovieWithActorRecord(
    val id: Long = 0L,
    val name: String,
    val producerName: String,
    val releaseDate: String,
    val actors: List<ActorRecord> = emptyList(),
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Stable JSON error shape for Ktor example failures.
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
