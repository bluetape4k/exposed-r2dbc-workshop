package exposed.r2dbc.examples.springbootrepository.controller

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.service.ProductTransactionService
import exposed.r2dbc.examples.springbootrepository.support.AbstractProductDatabaseTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull

/**
 * Product suspend endpoint의 상태·JSON·검증·삭제 계약을 검증합니다.
 */
@ResourceLock("issue-204-products-h2", mode = ResourceAccessMode.READ_WRITE)
class ProductControllerTest : AbstractProductDatabaseTest() {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var service: ProductTransactionService

    @Test
    fun `get products materializes the response Flow`() = runTest {
        val products = client.get().uri("/products")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .returnResult<ProductRecord>()
            .responseBody
            .asFlow()
            .toList()

        products.size shouldBeEqualTo 2
        products.all { it.id != null }.shouldBeTrue()
    }

    @Test
    fun `get missing product returns not found`() {
        client.get().uri("/products/999999")
            .exchange()
            .expectStatus().isNotFound
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Product를 찾을 수 없습니다")
    }

    @Test
    fun `post product returns generated id and does not accept id`() {
        val response = client.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Keyboard","description":"USB"}""")
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(ProductRecord::class.java)
            .returnResult()
            .responseBody.shouldNotBeNull()

        response.id.shouldNotBeNull()
        response.name shouldBeEqualTo "Keyboard"
    }

    @Test
    fun `delete existing and missing product map to explicit statuses`() = runTest {
        val existing = service.findByName("Notebook")
        val id = existing?.id.shouldNotBeNull()

        client.delete().uri("/products/{id}", id)
            .exchange()
            .expectStatus().isNoContent
            .expectBody().isEmpty

        client.delete().uri("/products/{id}", id)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `unknown id is rejected without changing rows or disclosing internals`() = runTest {
        val before = service.count()

        val body = client.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"id":123,"name":"Rejected","description":null}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody.orEmpty()

        service.count() shouldBeEqualTo before
        listOf("password", "r2dbc:", "select ", "secret").none(body.lowercase()::contains).shouldBeTrue()
    }

    @Test
    fun `invalid bounded fields return problem details before persistence`() = runTest {
        val before = service.count()
        val invalidPayloads = listOf(
            """{"name":"   ","description":null}""",
            """{"name":"${"x".repeat(121)}","description":null}""",
            """{"name":"Valid","description":"${"x".repeat(501)}"}""",
        )

        invalidPayloads.forEach { payload ->
            client.post().uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        }

        service.count() shouldBeEqualTo before
    }
}
