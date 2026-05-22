package exposed.r2dbc.multitenant.onboarding.domain.model

import java.io.Serializable

/**
 * DTO for movie rows in the tenant movie schema.
 */
data class MovieRecord(
    val id: Long = 0L,
    val name: String,
    val producerName: String,
    val releaseDate: String,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    fun withId(id: Long) = copy(id = id)
}

/**
 * DTO for actor rows in the tenant movie schema.
 */
data class ActorRecord(
    val id: Long = 0L,
    val firstName: String,
    val lastName: String,
    val birthday: String? = null,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    fun withId(id: Long) = copy(id = id)
}

/**
 * Join DTO for a movie and actor pair.
 */
data class MovieActorRecord(
    val movieId: Long,
    val actorId: Long,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Projection DTO for a movie name and actor count.
 */
data class MovieActorCountRecord(
    val movieName: String,
    val actorCount: Int,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * DTO for a movie and its actors.
 */
data class MovieWithActorRecord(
    val id: Long = 0L,
    val name: String,
    val producerName: String,
    val releaseDate: String,
    val actors: MutableList<ActorRecord> = mutableListOf(),
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Projection DTO for a movie name and producing actor name.
 */
data class MovieWithProducingActorRecord(
    val movieName: String,
    val producerActorName: String,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
