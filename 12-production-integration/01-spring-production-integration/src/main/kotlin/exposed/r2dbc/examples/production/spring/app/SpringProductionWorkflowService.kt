package exposed.r2dbc.examples.production.spring.app

import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import kotlin.time.TimeSource

/**
 * 코루틴과 WebFlux 경계를 명시적으로 유지하는 application service입니다.
 */
@Service
class SpringProductionWorkflowService(
    private val repository: SpringProductionRepository,
    private val realtimeDelivery: RealtimeDelivery,
    private val outboundDelivery: OutboundDelivery,
) {
    private companion object {
        const val slowDiagnosticThresholdMs = 250L
        const val maxDiagnosticDelayMs = 2_000L
        val operationNamePattern = Regex("[a-z][a-z0-9-]{0,40}")
    }

    suspend fun registerAccount(request: RegisterAccountRequest): AccountView =
        repository.registerAccount(request)

    suspend fun profile(username: String): AuthProfileView {
        val account = requireNotNull(repository.findAccount(username)) {
            "Authenticated account was not found: $username"
        }
        return AuthProfileView(account.username, account.displayName, account.roles)
    }

    suspend fun adminProfile(username: String): AuthProfileView {
        val profile = profile(username)
        if ("ADMIN" !in profile.roles) {
            throw PermissionDeniedException("ADMIN")
        }
        return profile
    }

    suspend fun createSession(username: String): SessionView =
        repository.createSession(username)

    suspend fun sessions(username: String): SessionsView =
        SessionsView(repository.findSessions(username))

    suspend fun createWorkItem(request: CreateWorkItemRequest): WorkItemView =
        repository.createWorkItem(request)

    suspend fun replayEvents(afterSequence: Long): List<OutboxEventView> =
        repository.replayEvents(afterSequence)

    suspend fun outboxEvents(): OutboxEventsView =
        OutboxEventsView(repository.outboxEvents())

    suspend fun publishPendingOutbox(): PublishOutboxView =
        repository.publishPending(realtimeDelivery)

    suspend fun enqueueOutbound(request: EnqueueOutboundRequest): OutboundRequestView =
        repository.enqueueOutbound(request)

    suspend fun outboundRequests(): OutboundRequestsView =
        OutboundRequestsView(repository.outboundRequests())

    suspend fun dispatchPendingOutbound(): DispatchOutboundView =
        repository.dispatchPendingOutbound(outboundDelivery)

    suspend fun runDiagnosticOperation(
        name: String,
        delayMs: Long,
        requestId: String,
    ): DiagnosticOperationView {
        require(operationNamePattern.matches(name)) {
            "operation name must start with a lowercase letter and contain only lowercase letters, digits, or hyphen"
        }
        require(delayMs in 0..maxDiagnosticDelayMs) {
            "delayMs must be between 0 and $maxDiagnosticDelayMs"
        }

        val started = TimeSource.Monotonic.markNow()
        if (delayMs > 0) {
            delay(delayMs)
        }
        val durationMs = started.elapsedNow().inWholeMilliseconds
        return repository.recordDiagnosticOperation(
            RecordDiagnosticOperationCommand(
                name = name,
                requestId = requestId,
                durationMs = durationMs,
                slow = durationMs >= slowDiagnosticThresholdMs,
            )
        )
    }

    suspend fun diagnosticOperations(): DiagnosticOperationsView =
        DiagnosticOperationsView(repository.diagnosticOperations())

    suspend fun readiness(requestId: String? = null): ReadinessView =
        repository.readiness(requestId)

    suspend fun requirePermission(apiKey: String, permission: String) {
        if (!repository.hasPermission(apiKey, permission)) {
            throw PermissionDeniedException(permission)
        }
    }
}
