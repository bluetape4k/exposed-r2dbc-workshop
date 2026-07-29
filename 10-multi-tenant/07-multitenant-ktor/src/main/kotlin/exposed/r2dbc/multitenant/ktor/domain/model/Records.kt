package exposed.r2dbc.multitenant.ktor.domain.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Ktor multi-tenant 예제가 반환하는 actor row다.
 *
 * @property id tenant schema 안에서 발급된 actor 식별자. 새 row를 만들기 전에는 `0`을 기본값으로 둔다.
 * @property firstName actor의 이름. 요청 검증에서 blank 값을 거부한 뒤 저장한다.
 * @property lastName actor의 성. tenant별 조회 결과가 섞이지 않는지 검증하는 표시 값으로도 사용한다.
 * @property birthday actor 생일 문자열. 예제에서는 nullable 값 저장과 JSON 응답 직렬화를 확인한다.
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
 * 현재 tenant schema에 actor를 생성할 때 사용하는 요청 body다.
 *
 * @property firstName 생성할 actor의 이름. route layer에서 blank 여부를 검증한다.
 * @property lastName 생성할 actor의 성. tenant별 write isolation 검증에 사용된다.
 * @property birthday 선택 생일 문자열. 값이 없으면 `null`로 유지되어 nullable column 흐름을 보여준다.
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
 * seed data와 chapter comparison 문서에서 사용하는 movie row다.
 *
 * @property id tenant schema 안에서 발급된 movie 식별자. seed insert 후 [withId]로 확정 값을 반영한다.
 * @property name movie 제목. Spring WebFlux 예제와 비교 가능한 fixture 이름을 유지한다.
 * @property producerName 제작자 이름. actor join 예제에서 관계 확인용 표시 값으로 사용한다.
 * @property releaseDate release date를 문자열로 직렬화해 Ktor JSON 응답을 단순하게 유지한다.
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
 * seed data 생성 중 사용하는 movie와 actor 목록 묶음이다.
 *
 * seed setup 내부 전용 타입이므로 JSON serializer가 필요하지 않다.
 *
 * @property id movie 식별자. seed 생성 전에는 `0`으로 두고 insert 후 확정한다.
 * @property name movie 제목.
 * @property producerName 제작자 이름.
 * @property releaseDate release date 문자열.
 * @property actors movie에 연결할 actor 목록. 기본값은 빈 목록이며 seed join row 생성에 사용한다.
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
 * Ktor 예제 실패 응답에 사용하는 안정적인 JSON 오류 형태다.
 *
 * @property code 클라이언트가 분기할 수 있는 고정 오류 코드.
 * @property message 사용자에게 전달할 상세 메시지. raw tenant header 값은 그대로 반영하지 않는다.
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
