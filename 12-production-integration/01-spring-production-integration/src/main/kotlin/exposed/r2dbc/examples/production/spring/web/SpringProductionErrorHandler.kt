package exposed.r2dbc.examples.production.spring.web

import exposed.r2dbc.examples.production.spring.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.spring.app.PermissionDeniedException
import exposed.r2dbc.examples.production.spring.app.StructuredError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebInputException

/**
 * Converts workshop exceptions into stable structured HTTP responses.
 */
@RestControllerAdvice
class SpringProductionErrorHandler {
    @ExceptionHandler(DuplicateIdempotencyKeyException::class)
    fun duplicateIdempotencyKey(exception: DuplicateIdempotencyKeyException): ResponseEntity<StructuredError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(StructuredError("IDEMPOTENCY_CONFLICT", exception.message ?: "Duplicate idempotency key"))

    @ExceptionHandler(PermissionDeniedException::class)
    fun permissionDenied(exception: PermissionDeniedException): ResponseEntity<StructuredError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(StructuredError("FORBIDDEN", exception.message ?: "Permission denied"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException): ResponseEntity<StructuredError> =
        ResponseEntity.badRequest()
            .body(StructuredError("INVALID_REQUEST", exception.message ?: "Invalid request"))

    @ExceptionHandler(ServerWebInputException::class)
    fun malformedRequest(): ResponseEntity<StructuredError> =
        ResponseEntity.badRequest()
            .body(StructuredError("INVALID_JSON", "Request body is not valid JSON"))
}
