package exposed.r2dbc.examples.cache.ktor.domain

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Cache status exposed by read and write routes.
 */
@Serializable
enum class CacheStatus {
    HIT,
    MISS,
    NOT_FOUND,
    WRITTEN,
}

/**
 * User row used by the Ktor cache strategy example.
 */
@Serializable
data class UserRecord(
    val id: Long = 0L,
    val username: String,
    val firstName: String,
    val lastName: String,
    val address: String? = null,
    val zipcode: String? = null,
    val birthDate: String? = null,
): JavaSerializable {
    fun withId(id: Long): UserRecord = copy(id = id)

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Request body for creating or updating a user.
 */
@Serializable
data class UpsertUserRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val address: String? = null,
    val zipcode: String? = null,
    val birthDate: String? = null,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Route response that makes cache behavior observable.
 */
@Serializable
data class UserCacheResponse(
    val cacheStatus: CacheStatus,
    val user: UserRecord?,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Cache invalidation route response.
 */
@Serializable
data class InvalidationResponse(
    val invalidated: Long,
): JavaSerializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Application-local cache observation counters.
 */
@Serializable
data class CacheStatsRecord(
    val hits: Long,
    val misses: Long,
    val notFound: Long,
    val written: Long,
    val invalidated: Long,
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

class UserNotFoundException(id: Long): RuntimeException("user not found: $id")
