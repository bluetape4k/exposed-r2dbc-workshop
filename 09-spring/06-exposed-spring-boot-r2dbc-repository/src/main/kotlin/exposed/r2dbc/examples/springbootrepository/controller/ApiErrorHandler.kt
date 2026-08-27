package exposed.r2dbc.examples.springbootrepository.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.bind.support.WebExchangeBindException

/**
 * 입력 오류를 내부 SQL·R2DBC 세부사항 없이 표준 problem detail로 변환합니다.
 */
@RestControllerAdvice
class ApiErrorHandler {

    /** Bean Validation 실패를 `400 application/problem+json`으로 응답합니다. */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationFailure(@Suppress("UNUSED_PARAMETER") failure: WebExchangeBindException): ResponseEntity<ProblemDetail> =
        badRequest("요청 본문이 Product 입력 계약을 만족하지 않습니다")

    /** JSON decoding/unknown property 오류를 내부 예외 없이 응답합니다. */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleMalformedRequest(@Suppress("UNUSED_PARAMETER") failure: ServerWebInputException): ResponseEntity<ProblemDetail> =
        badRequest("요청 본문을 해석할 수 없습니다")

    private fun badRequest(detail: String): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail))
}
