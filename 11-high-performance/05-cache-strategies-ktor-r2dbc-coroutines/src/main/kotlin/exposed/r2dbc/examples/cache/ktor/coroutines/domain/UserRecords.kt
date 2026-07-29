package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * 코루틴 기반 읽기/쓰기 route가 응답에 노출하는 캐시 처리 상태입니다.
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
 * Ktor 코루틴 캐시 전략 예제에서 데이터베이스 행과 HTTP 응답을 함께 표현하는 사용자 record입니다.
 *
 * @property id 데이터베이스가 부여한 사용자 식별자입니다. 생성 요청에서는 `0L` 기본값을 사용하고,
 *   저장 후에는 실제 행 식별자로 교체합니다.
 * @property username 캐시 키와 조회 조건에 사용하는 고유 사용자 이름입니다.
 * @property firstName 예제 응답에서 표시하는 이름입니다.
 * @property lastName 예제 응답에서 표시하는 성입니다.
 * @property address 선택 입력 가능한 주소입니다. 값이 없으면 `null`로 직렬화됩니다.
 * @property zipcode 선택 입력 가능한 우편번호입니다. 값이 없으면 `null`로 직렬화됩니다.
 * @property birthDate ISO-8601 문자열로 전달되는 선택 생년월일입니다. 저장 시 `LocalDate`로 변환합니다.
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
 * 사용자 생성/수정 route가 수신하는 요청 본문입니다.
 *
 * @property username 생성 또는 수정할 사용자의 고유 이름입니다.
 * @property firstName 저장할 이름입니다.
 * @property lastName 저장할 성입니다.
 * @property address 선택 주소입니다. 누락하면 기존 저장 값 대신 `null`이 전달됩니다.
 * @property zipcode 선택 우편번호입니다. 누락하면 기존 저장 값 대신 `null`이 전달됩니다.
 * @property birthDate 선택 생년월일 문자열입니다. 저장 계층에서 `LocalDate` 변환 가능 여부를 검증합니다.
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
 * route 호출자가 캐시 동작을 관찰할 수 있도록 상태와 사용자 payload를 함께 반환하는 응답입니다.
 *
 * @property cacheStatus 요청이 캐시 hit, miss, coalesced, 미발견, 쓰기 중 어떤 경로를 거쳤는지 나타냅니다.
 * @property user 조회 또는 저장된 사용자입니다. 사용자를 찾지 못한 경우 `null`입니다.
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
 * 캐시 무효화 route가 제거한 항목 수를 반환하는 응답입니다.
 *
 * @property invalidated 단일 사용자 또는 전체 캐시 무효화에서 제거된 cache entry 수입니다.
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
 * 애플리케이션 프로세스 안에서 집계한 코루틴 캐시 관찰 counter입니다.
 *
 * @property hits 캐시에서 바로 반환한 조회 횟수입니다.
 * @property misses 캐시에 없어서 데이터베이스에서 적재한 조회 횟수입니다.
 * @property coalesced 같은 키를 동시에 적재하려는 요청이 하나의 loader 실행으로 합쳐진 횟수입니다.
 * @property notFound 데이터베이스에도 사용자가 없어 `404`로 응답한 횟수입니다.
 * @property written 생성/수정 route가 캐시 갱신까지 완료한 횟수입니다.
 * @property invalidated 단일 사용자 또는 전체 캐시 무효화가 수행된 횟수입니다.
 * @property cancellations loader 실행 중 코루틴 취소가 관찰된 횟수입니다.
 * @property loadFailures 데이터베이스 적재 또는 변환 단계에서 실패한 횟수입니다.
 * @property cacheWriteFailures 데이터베이스 쓰기 후 캐시 갱신 단계에서 실패한 횟수입니다.
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
 * Ktor 예제 실패를 안정적인 JSON 형식으로 반환하기 위한 오류 응답입니다.
 *
 * @property code 클라이언트가 분기할 수 있는 짧은 오류 코드입니다.
 * @property message 사람이 읽을 수 있는 오류 설명입니다.
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
 * 요청한 사용자가 데이터베이스에 존재하지 않을 때 발생하는 예외입니다.
 */
class UserNotFoundException(id: Long): RuntimeException("user not found: $id")

/**
 * 테스트 전용 선택 load delay가 허용 범위를 벗어났을 때 발생하는 예외입니다.
 */
class InvalidLoadDelayException(delayMillis: Long):
    IllegalArgumentException("loadDelayMillis must be between 0 and 1000: $delayMillis")
