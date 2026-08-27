package exposed.r2dbc.examples.springbootrepository.controller

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.service.ProductTransactionService
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid

/**
 * provider repository를 사용하는 최소 Product suspend HTTP API입니다.
 *
 * 목록은 의도적으로 `Flow`를 HTTP 응답 전에 materialize하고, 개별 조회/삭제의
 * not-found 상태와 생성 응답의 서버 발급 ID를 명시적인 HTTP 계약으로 고정합니다.
 */
@Validated
@RestController
@RequestMapping("/products")
class ProductController(
    private val service: ProductTransactionService,
) {

    /** Product 전체를 `200 application/json` 배열로 반환합니다. */
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findAll(): List<ProductRecord> = service.findAll().toList()

    /** Product를 조회하고 없으면 `404 application/problem+json`을 반환합니다. */
    @GetMapping(
        "/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE],
    )
    suspend fun findById(@PathVariable id: Long): ResponseEntity<Any> =
        service.findByIdOrNull(id)?.let { ResponseEntity.ok<Any>(it) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product를 찾을 수 없습니다"))

    /** 검증된 요청을 저장하고 생성된 ID와 함께 `201 Created`를 반환합니다. */
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun create(@Valid @RequestBody request: ProductCreateRequest): ResponseEntity<ProductRecord> =
        ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(service.save(ProductRecord(id = null, name = request.name, description = request.description)))

    /** 존재하는 Product를 삭제하고 `204`를, 없으면 `404`를 반환합니다. */
    @DeleteMapping("/{id}")
    suspend fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (service.deleteIfExists(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
}
