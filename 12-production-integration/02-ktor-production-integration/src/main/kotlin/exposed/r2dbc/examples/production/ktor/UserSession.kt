package exposed.r2dbc.examples.production.ktor

import java.io.Serializable

/**
 * Ktor session cookie에 저장하는 세션 식별 정보입니다.
 */
@kotlinx.serialization.Serializable
data class UserSession(
    val token: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
