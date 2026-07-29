package exposed.r2dbc.examples.production.spring.web

import exposed.r2dbc.examples.production.spring.app.DuplicateIdempotencyKeyException
import exposed.r2dbc.examples.production.spring.app.PermissionDeniedException
import exposed.r2dbc.examples.production.spring.app.StructuredError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

/**
 * workshop 예외를 안정적인 구조화 HTTP 응답으로 변환합니다.
 */
@RestControllerAdvice
class SpringProductionErrorHandler {
    @ExceptionHandler(DuplicateIdempotencyKeyException::class)
    fun duplicateIdempotencyKey(
        exception: DuplicateIdempotencyKeyException,
        exchange: ServerWebExchange,
    ): ResponseEntity<StructuredError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                StructuredError(
                    code = "IDEMPOTENCY_CONFLICT",
                    message = exception.message ?: "Duplicate idempotency key",
                    requestId = exchange.requestId(),
                )
            )

    @ExceptionHandler(PermissionDeniedException::class)
    fun permissionDenied(
        exception: PermissionDeniedException,
        exchange: ServerWebExchange,
    ): ResponseEntity<StructuredError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                StructuredError(
                    code = "FORBIDDEN",
                    message = exception.message ?: "Permission denied",
                    requestId = exchange.requestId(),
                )
            )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(
        exception: IllegalArgumentException,
        exchange: ServerWebExchange,
    ): ResponseEntity<StructuredError> =
        ResponseEntity.badRequest()
            .body(
                StructuredError(
                    code = "INVALID_REQUEST",
                    message = exception.message ?: "Invalid request",
                    requestId = exchange.requestId(),
                )
            )

    @ExceptionHandler(ServerWebInputException::class)
    fun malformedRequest(exchange: ServerWebExchange): ResponseEntity<StructuredError> =
        ResponseEntity.badRequest()
            .body(
                StructuredError(
                    code = "INVALID_JSON",
                    message = "Request body is not valid JSON",
                    requestId = exchange.requestId(),
                )
            )
}
