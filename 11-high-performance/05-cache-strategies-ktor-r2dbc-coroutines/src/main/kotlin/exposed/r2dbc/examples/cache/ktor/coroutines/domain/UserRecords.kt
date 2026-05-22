package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Cache status exposed by read and write routes.
 */
@Serializable
enum class CacheStatus {
    HIT,
    MISS,
    COALESCED,
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
data class CoroutineUserCacheResponse(
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
data class CoroutineCacheStatsRecord(
    val hits: Long,
    val misses: Long,
    val coalesced: Long,
    val notFound: Long,
    val written: Long,
    val invalidated: Long,
    val cancellations: Long,
    val loadFailures: Long,
    val cacheWriteFailures: Long,
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

/**
 * Raised when a requested user does not exist.
 */
class UserNotFoundException(id: Long): RuntimeException("user not found: $id")

/**
 * Raised when the optional test-only load delay is outside the accepted range.
 */
class InvalidLoadDelayException(delayMillis: Long):
    IllegalArgumentException("loadDelayMillis must be between 0 and 1000: $delayMillis")
