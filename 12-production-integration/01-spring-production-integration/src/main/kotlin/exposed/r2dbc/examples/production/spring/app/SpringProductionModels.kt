package exposed.r2dbc.examples.production.spring.app

import java.io.Serializable

/**
 * 계정과 workshop 권한을 등록하는 요청입니다.
 *
 * @property username 로그인과 감사 기록에 사용하는 고유 계정 이름입니다.
 * @property apiKey 예제 API 경계에서 권한 확인에 사용하는 외부 호출 키입니다.
 * @property permission 계정이 수행할 수 있는 workshop 권한 이름입니다.
 * @property password Spring Security 인증에 저장할 원문 비밀번호입니다. repository에서 hash로 변환합니다.
 * @property displayName 응답과 세션 profile에 표시할 이름입니다. 생략하면 [username]을 사용합니다.
 * @property roles Spring Security 권한 매핑에 사용할 role 집합입니다.
 */
data class RegisterAccountRequest(
    val username: String,
    val apiKey: String,
    val permission: String,
    val password: String = "password",
    val displayName: String = username,
    val roles: Set<String> = setOf("USER"),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 검증된 production 작업 항목을 생성하는 요청입니다.
 *
 * @property owner 작업 항목을 소유하는 계정 또는 tenant 식별자입니다.
 * @property payload 업무 payload입니다. 예제에서는 저장/조회 경계 보존을 확인하는 문자열로 사용합니다.
 */
data class CreateWorkItemRequest(
    val owner: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * dispatch 전에 반드시 저장해야 하는 outbound 호출 요청입니다.
 *
 * @property idempotencyKey 중복 dispatch를 막는 idempotency key입니다.
 * @property targetUrl outbound HTTP 요청을 보낼 대상 URL입니다.
 * @property payload 대상 시스템으로 전송할 요청 본문입니다.
 */
data class EnqueueOutboundRequest(
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 인증 slice가 반환하는 계정 응답입니다.
 *
 * @property id repository가 생성한 계정 식별자입니다.
 * @property username 로그인과 조회에 사용하는 계정 이름입니다.
 * @property displayName 사용자에게 표시할 계정 이름입니다.
 * @property permission 계정에 부여된 workshop 권한입니다.
 * @property roles Spring Security에 노출할 role 집합입니다.
 */
data class AccountView(
    val id: String,
    val username: String,
    val displayName: String,
    val permission: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Spring Security slice가 반환하는 인증된 profile입니다.
 *
 * @property username 인증된 principal의 계정 이름입니다.
 * @property displayName 응답에 표시할 계정 이름입니다.
 * @property roles 인증 후 부여된 role 집합입니다.
 */
data class AuthProfileView(
    val username: String,
    val displayName: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 인증 slice가 저장하는 세션 metadata입니다.
 *
 * @property token 세션 cookie 또는 응답으로 전달되는 토큰입니다. 목록 조회에서는 마스킹될 수 있습니다.
 * @property username 세션을 발급받은 계정 이름입니다.
 * @property issuedAtEpochMs 세션 발급 시각입니다.
 * @property expiresAtEpochMs 세션 만료 시각입니다.
 */
data class SessionView(
    val token: String?,
    val username: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 저장된 세션 metadata 목록을 감싸는 응답입니다.
 *
 * @property sessions 현재 repository에 저장된 세션 목록입니다.
 */
data class SessionsView(
    val sessions: List<SessionView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * application slice가 반환하는 작업 항목 응답입니다.
 *
 * @property id 작업 항목 식별자입니다.
 * @property owner 작업 항목 소유자입니다.
 * @property payload 저장된 업무 payload입니다.
 * @property status 처리 상태입니다. 예제에서는 검증/저장 흐름 확인에 사용합니다.
 */
data class WorkItemView(
    val id: String,
    val owner: String,
    val payload: String,
    val status: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * realtime outbox slice가 반환하는 저장된 event입니다.
 *
 * @property sequence outbox 행의 단조 증가 순번입니다.
 * @property aggregateId event가 속한 aggregate 식별자입니다.
 * @property eventType replay 소비자가 분기할 event 종류입니다.
 * @property payload realtime 구독자에게 전달할 event payload입니다.
 * @property status event 전달 상태입니다.
 * @property attempts publish를 시도한 횟수입니다.
 * @property lastError 마지막 publish 실패 사유입니다. 성공 또는 미시도 상태에서는 `null`일 수 있습니다.
 */
data class OutboxEventView(
    val sequence: Long,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxStatus,
    val attempts: Int,
    val lastError: String?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 저장된 realtime outbox 행 목록을 감싸는 응답입니다.
 *
 * @property events replay 또는 조회 route가 반환한 outbox event 목록입니다.
 */
data class OutboxEventsView(
    val events: List<OutboxEventView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * realtime outbox publish 시도 결과 요약입니다.
 *
 * @property attempted publish 대상으로 선택한 행 수입니다.
 * @property delivered 성공적으로 전달한 행 수입니다.
 * @property failed 전달 실패로 남긴 행 수입니다.
 */
data class PublishOutboxView(
    val attempted: Int,
    val delivered: Int,
    val failed: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 각 realtime outbox 행에 저장하는 명시적 전달 상태입니다.
 */
enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}

/**
 * realtime outbox publisher가 사용하는 전달 경계입니다.
 */
interface RealtimeDelivery {
    suspend fun deliver(event: OutboxEventView): Boolean
}

/**
 * HTTP client outbox slice가 반환하는 outbound 요청 응답입니다.
 *
 * @property id outbound 요청 식별자입니다.
 * @property idempotencyKey 중복 저장/dispatch를 막는 idempotency key입니다.
 * @property targetUrl HTTP 요청 대상 URL입니다.
 * @property payload 대상 시스템으로 보낼 요청 본문입니다.
 * @property status outbound 요청 전달 상태입니다.
 * @property attempts HTTP dispatch 시도 횟수입니다.
 * @property lastStatusCode 마지막 HTTP 응답 status code입니다. 요청 전 또는 네트워크 실패 시 `null`입니다.
 * @property lastError 마지막 실패 사유입니다. 성공 또는 미시도 상태에서는 `null`일 수 있습니다.
 */
data class OutboundRequestView(
    val id: String,
    val idempotencyKey: String,
    val targetUrl: String,
    val payload: String,
    val status: OutboundStatus,
    val attempts: Int,
    val lastStatusCode: Int?,
    val lastError: String?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 저장된 outbound HTTP 행 목록을 감싸는 응답입니다.
 *
 * @property requests 조회 route가 반환한 outbound 요청 목록입니다.
 */
data class OutboundRequestsView(
    val requests: List<OutboundRequestView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * outbound HTTP dispatch 시도 결과 요약입니다.
 *
 * @property attempted dispatch 대상으로 선택한 행 수입니다.
 * @property succeeded 성공적으로 전달한 행 수입니다.
 * @property retryableFailed 재시도 가능한 실패로 남긴 행 수입니다.
 * @property permanentFailed 영구 실패로 확정한 행 수입니다.
 */
data class DispatchOutboundView(
    val attempted: Int,
    val succeeded: Int,
    val retryableFailed: Int,
    val permanentFailed: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 각 outbound HTTP 행에 저장하는 명시적 전달 상태입니다.
 */
enum class OutboundStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    RETRYABLE_FAILED,
    PERMANENT_FAILED,
}

/**
 * outbound HTTP client 경계가 반환하는 dispatch 결과입니다.
 *
 * @property statusCode 대상 시스템 또는 synthetic failure에서 받은 HTTP status code입니다.
 * @property error 실패 내용을 정규화한 오류 문자열입니다. 성공 응답에서는 `null`입니다.
 */
data class OutboundDispatchResult(
    val statusCode: Int,
    val error: String? = null,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * outbound outbox dispatcher가 사용하는 HTTP 전달 경계입니다.
 */
interface OutboundDelivery {
    suspend fun dispatch(request: OutboundRequestView): OutboundDispatchResult
}

/**
 * diagnostics slice가 반환하는 readiness 응답입니다.
 *
 * @property status readiness 상태 문자열입니다.
 * @property details 상태 판단에 대한 세부 설명입니다.
 * @property requestId 요청 상관관계 식별자입니다. 없으면 `null`입니다.
 */
data class ReadinessView(
    val status: String,
    val details: String,
    val requestId: String? = null,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * diagnostics slice가 반환하는 저장된 진단 작업입니다.
 *
 * @property id 진단 작업 식별자입니다.
 * @property name 측정한 작업 이름입니다.
 * @property requestId 요청 상관관계 식별자입니다.
 * @property durationMs 작업 소요 시간입니다.
 * @property slow 느린 작업 기준을 넘었는지 여부입니다.
 * @property createdAtEpochMs 진단 작업이 저장된 시각입니다.
 */
data class DiagnosticOperationView(
    val id: String,
    val name: String,
    val requestId: String,
    val durationMs: Long,
    val slow: Boolean,
    val createdAtEpochMs: Long,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 진단 작업 행 목록을 감싸는 응답입니다.
 *
 * @property operations 조회된 진단 작업 목록입니다.
 */
data class DiagnosticOperationsView(
    val operations: List<DiagnosticOperationView>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Spring이 사용하고 Ktor module이 동일하게 맞추는 구조화된 API 오류입니다.
 *
 * @property code 클라이언트가 분기할 수 있는 짧은 오류 코드입니다.
 * @property message 사람이 읽을 수 있는 오류 설명입니다.
 * @property requestId 오류가 발생한 요청의 상관관계 식별자입니다. 없으면 `null`입니다.
 */
data class StructuredError(
    val code: String,
    val message: String,
    val requestId: String? = null,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 측정된 진단 작업 하나를 저장하기 위한 repository command입니다.
 *
 * @property name 저장할 작업 이름입니다.
 * @property requestId 요청 상관관계 식별자입니다.
 * @property durationMs 작업 소요 시간입니다.
 * @property slow 느린 작업 기준을 넘었는지 여부입니다.
 */
data class RecordDiagnosticOperationCommand(
    val name: String,
    val requestId: String,
    val durationMs: Long,
    val slow: Boolean,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * outbound idempotency key가 이미 저장되어 있음을 알리는 예외입니다.
 */
class DuplicateIdempotencyKeyException(
    key: String,
): RuntimeException("Duplicate idempotency key: $key")

/**
 * 인증은 성공했지만 필요한 role이 부족함을 알리는 예외입니다.
 */
class PermissionDeniedException(
    permission: String,
): RuntimeException("$permission permission is required")

/**
 * repository가 소유하는 인증 계정 record입니다.
 *
 * @property id 계정 식별자입니다.
 * @property username 로그인과 조회에 사용하는 계정 이름입니다.
 * @property passwordHash 저장된 비밀번호 hash입니다. 원문 비밀번호는 노출하지 않습니다.
 * @property displayName 응답과 profile에 표시할 이름입니다.
 * @property permission 계정에 부여된 workshop 권한입니다.
 * @property roles 인증 후 부여할 role 집합입니다.
 */
data class AuthAccount(
    val id: String,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val permission: String,
    val roles: Set<String>,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
