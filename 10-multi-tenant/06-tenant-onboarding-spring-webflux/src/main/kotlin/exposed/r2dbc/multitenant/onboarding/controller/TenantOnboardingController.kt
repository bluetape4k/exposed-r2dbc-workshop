package exposed.r2dbc.multitenant.onboarding.controller

import exposed.r2dbc.multitenant.onboarding.tenant.TenantHeaders
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingProperties
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingRequest
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingResponse
import exposed.r2dbc.multitenant.onboarding.tenant.TenantProvisioner
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Admin tenant onboarding API.
 */
@RestController
class TenantOnboardingController(
    private val properties: TenantOnboardingProperties,
    private val provisioner: TenantProvisioner,
) {

    @PostMapping("/api/tenants")
    suspend fun onboard(
        @RequestHeader(TenantHeaders.ADMIN_TOKEN_HEADER, required = false) adminToken: String?,
        @RequestBody request: TenantOnboardingRequest,
    ): ResponseEntity<TenantOnboardingResponse> {
        requireAdmin(adminToken)
        val metadata = provisioner.onboard(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TenantOnboardingResponse.from(metadata))
    }

    private fun requireAdmin(candidate: String?) {
        val expected = properties.adminToken.toByteArray(StandardCharsets.UTF_8)
        val actual = candidate.orEmpty().toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(expected, actual)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token")
        }
    }
}
