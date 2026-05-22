package exposed.r2dbc.examples.production.ktor

import java.io.Serializable

/**
 * Session identity stored in the Ktor session cookie.
 */
@kotlinx.serialization.Serializable
data class UserSession(
    val token: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
