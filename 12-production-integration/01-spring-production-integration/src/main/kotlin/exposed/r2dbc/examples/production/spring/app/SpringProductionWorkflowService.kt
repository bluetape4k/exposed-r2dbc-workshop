package exposed.r2dbc.examples.production.spring.app

import org.springframework.stereotype.Service

/**
 * Application service that keeps coroutine and WebFlux boundaries explicit.
 */
@Service
class SpringProductionWorkflowService(
    private val repository: SpringProductionRepository,
) {
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

    suspend fun enqueueOutbound(request: EnqueueOutboundRequest): OutboundRequestView =
        repository.enqueueOutbound(request)

    suspend fun readiness(): ReadinessView =
        repository.readiness()

    suspend fun requirePermission(apiKey: String, permission: String) {
        require(repository.hasPermission(apiKey, permission)) { "Missing permission: $permission" }
    }
}
