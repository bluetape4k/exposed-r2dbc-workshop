package exposed.r2dbc.multitenant.onboarding.controller

import exposed.r2dbc.multitenant.onboarding.tenant.DuplicateTenantException
import exposed.r2dbc.multitenant.onboarding.tenant.InvalidTenantRequestException
import exposed.r2dbc.multitenant.onboarding.tenant.TenantLimitExceededException
import exposed.r2dbc.multitenant.onboarding.tenant.TenantProvisioningException
import exposed.r2dbc.multitenant.onboarding.tenant.UnknownTenantException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.io.Serializable

/**
 * Maps tenant onboarding and routing failures to stable JSON error responses.
 */
@RestControllerAdvice
class TenantApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(error: ResponseStatusException): ResponseEntity<ApiError> =
        ResponseEntity.status(error.statusCode)
            .body(ApiError(error.statusCode.value(), error.reason ?: "Request failed"))

    @ExceptionHandler(InvalidTenantRequestException::class)
    fun invalidTenantRequest(error: InvalidTenantRequestException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError(HttpStatus.BAD_REQUEST.value(), error.message ?: "Invalid tenant request"))

    @ExceptionHandler(DuplicateTenantException::class)
    fun duplicate(error: DuplicateTenantException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError(HttpStatus.CONFLICT.value(), error.message ?: "Duplicate tenant"))

    @ExceptionHandler(UnknownTenantException::class)
    fun unknown(error: UnknownTenantException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError(HttpStatus.BAD_REQUEST.value(), error.message ?: "Unknown tenant"))

    @ExceptionHandler(TenantLimitExceededException::class)
    fun tenantLimit(error: TenantLimitExceededException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), error.message ?: "Tenant limit exceeded"))

    @ExceptionHandler(TenantProvisioningException::class)
    fun provisioning(error: TenantProvisioningException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), error.message ?: "Tenant provisioning failed"))
}

data class ApiError(
    val status: Int,
    val message: String,
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
