package exposed.r2dbc.examples.springbootrepository.service

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.support.AbstractProductDatabaseTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired

/**
 * provider의 단일 호출 transaction과 애플리케이션 소유 outer transaction 경계를 검증합니다.
 */
@ResourceLock("issue-204-products-h2", mode = ResourceAccessMode.READ_WRITE)
class ProductTransactionServiceTest : AbstractProductDatabaseTest() {

    @Autowired
    private lateinit var service: ProductTransactionService

    @Test
    fun `outer transaction commits two saves together`() = runTest {
        val saved = service.saveTwoAtomically(
            ProductRecord(null, "Atomic one", null),
            ProductRecord(null, "Atomic two", "description"),
        )

        saved.size shouldBeEqualTo 2
        saved.all { it.id != null }.shouldBeTrue()
        service.count() shouldBeEqualTo 4L
    }

    @Test
    fun `outer transaction rolls back two valid saves on failure`() = runTest {
        assertFailsWith<IllegalStateException> {
            service.saveTwoAndFailAtomically(
                ProductRecord(null, "Rollback one", null),
                ProductRecord(null, "Rollback two", null),
            )
        }

        service.count() shouldBeEqualTo 2L
    }

    @Test
    fun `without outer transaction first call remains after second constraint failure`() = runTest {
        val first = ProductRecord(null, "Committed first", null)
        val invalidSecond = ProductRecord(null, "x".repeat(121), null)

        // bluetape4k-exposed 2.x direct proxy는 reflection target exception을 그대로 재전파합니다.
        assertFailsWith<IllegalArgumentException> {
            service.saveTwoAndFailWithoutOuterTransaction(first, invalidSecond)
        }

        service.count() shouldBeEqualTo 3L
        service.findByName("Committed first").shouldNotBeNull()
    }

    @Test
    fun `deleteIfExists reports atomic delete result`() = runTest {
        val existing = service.findByName("Notebook")
        val existingId = existing?.id.shouldNotBeNull()

        service.deleteIfExists(existingId).shouldBeTrue()
        service.deleteIfExists(existingId).shouldBeFalse()
        service.count() shouldBeEqualTo 1L
    }

}
