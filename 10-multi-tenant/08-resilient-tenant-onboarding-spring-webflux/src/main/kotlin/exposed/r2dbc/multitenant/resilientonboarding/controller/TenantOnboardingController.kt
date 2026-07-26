package exposed.r2dbc.multitenant.resilientonboarding.controller

import exposed.r2dbc.multitenant.resilientonboarding.tenant.ResilientTenantProvisioner
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantId
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleRepository
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantMetadata
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantOnboardingCommand
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantOnboardingResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class TenantOnboardingPayload(
    val tenantId: String,
    val displayName: String,
)

data class TenantLifecycleResponse(
    val tenantId: String,
    val displayName: String,
    val status: String,
    val attempt: Int,
    val failureCode: String?,
) {
    companion object {
        fun from(metadata: TenantMetadata): TenantLifecycleResponse =
            TenantLifecycleResponse(
                tenantId = metadata.tenantId.value,
                displayName = metadata.displayName,
                status = metadata.status.name,
                attempt = metadata.attempt,
                failureCode = metadata.lastFailureCode?.name,
            )
    }
}

@RestController
@RequestMapping("/api")
class TenantOnboardingController(
    private val provisioner: ResilientTenantProvisioner,
    private val repository: TenantLifecycleRepository,
    private val properties: TenantLifecycleProperties,
) {

    @PostMapping("/admin/tenants")
    suspend fun onboard(
        @RequestHeader("X-ADMIN-TOKEN", required = false) adminToken: String?,
        @RequestBody payload: TenantOnboardingPayload,
    ): ResponseEntity<TenantLifecycleResponse> {
        requireAdminToken(adminToken)
        val result = provisioner.onboard(
            TenantOnboardingCommand(TenantId(payload.tenantId.trim()), payload.displayName.trim()),
        )
        val metadata = result.metadata()
        val response = TenantLifecycleResponse.from(metadata)
        return when (result) {
            is TenantOnboardingResult.Created -> ResponseEntity.created(java.net.URI.create("/api/tenants/${metadata.tenantId.value}"))
                .body(response)
            is TenantOnboardingResult.Active -> ResponseEntity.ok(response)
            is TenantOnboardingResult.Pending -> ResponseEntity.accepted().body(response)
            is TenantOnboardingResult.Conflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(response)
            is TenantOnboardingResult.Failed -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @GetMapping("/tenants/{tenantId}")
    suspend fun get(
        @RequestHeader("X-ADMIN-TOKEN", required = false) adminToken: String?,
        @org.springframework.web.bind.annotation.PathVariable tenantId: String,
    ): TenantLifecycleResponse {
        requireAdminToken(adminToken)
        return repository.find(TenantId(tenantId))?.let(TenantLifecycleResponse::from)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant")
    }

    private fun requireAdminToken(adminToken: String?) {
        if (adminToken != properties.adminToken) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token")
        }
    }

    private fun TenantOnboardingResult.metadata(): TenantMetadata = when (this) {
        is TenantOnboardingResult.Created -> metadata
        is TenantOnboardingResult.Active -> metadata
        is TenantOnboardingResult.Pending -> metadata
        is TenantOnboardingResult.Conflict -> metadata
        is TenantOnboardingResult.Failed -> metadata
    }
}
