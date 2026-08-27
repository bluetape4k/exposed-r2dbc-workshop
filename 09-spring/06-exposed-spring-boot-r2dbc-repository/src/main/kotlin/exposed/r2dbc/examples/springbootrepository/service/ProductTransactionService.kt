package exposed.r2dbc.examples.springbootrepository.service

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.domain.Products
import exposed.r2dbc.examples.springbootrepository.repository.ProductR2dbcRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Service

/**
 * provider의 단일 호출 transaction과 애플리케이션 소유 outer transaction을 함께 보여 주는 서비스입니다.
 *
 * Spring `@Transactional` 대신 Exposed의 명시적인 [suspendTransaction] 경계를 사용합니다.
 */
@Service
class ProductTransactionService(
    private val database: R2dbcDatabase,
    private val repository: ProductR2dbcRepository,
) {

    /**
     * 단일 Product 저장을 provider proxy에 위임합니다.
     */
    suspend fun save(product: ProductRecord): ProductRecord = repository.save(product)

    /**
     * 식별자로 Product를 조회하고 없으면 `null`을 반환합니다.
     */
    suspend fun findByIdOrNull(id: Long): ProductRecord? = repository.findByIdOrNull(id)

    /**
     * 현재 Product 전체를 lazy Flow로 반환합니다.
     */
    fun findAll(): Flow<ProductRecord> = repository.findAll()

    /**
     * Product row 수를 provider proxy에 위임합니다.
     */
    suspend fun count(): Long = repository.count()

    /**
     * 테스트와 예제 조회에 사용할 이름 기반 단건 조회입니다.
     */
    suspend fun findByName(name: String): ProductRecord? =
        repository.findAll { Products.name eq name }.firstOrNull()

    /**
     * 두 저장을 하나의 명시적인 outer transaction으로 커밋합니다.
     */
    suspend fun saveTwoAtomically(first: ProductRecord, second: ProductRecord): List<ProductRecord> =
        suspendTransaction(db = database) {
            listOf(repository.save(first), repository.save(second))
        }

    /**
     * 두 유효한 저장 뒤 예외를 발생시켜 outer transaction 전체를 rollback합니다.
     */
    suspend fun saveTwoAndFailAtomically(first: ProductRecord, second: ProductRecord): Nothing =
        suspendTransaction(db = database) {
            repository.save(first)
            repository.save(second)
            error("issue-204-atomic-failure")
        }

    /**
     * outer transaction 없이 두 proxy 호출을 수행하여 partial commit 경계를 보여 줍니다.
     *
     * 호출자는 두 번째 입력을 의도적으로 column 제약에 맞지 않게 제공해야 합니다.
     */
    suspend fun saveTwoAndFailWithoutOuterTransaction(
        first: ProductRecord,
        second: ProductRecord,
    ): ProductRecord {
        repository.save(first)
        return repository.save(second)
    }

    /**
     * 존재 확인과 삭제를 같은 outer transaction에서 수행하고 삭제 여부를 반환합니다.
     */
    suspend fun deleteIfExists(id: Long): Boolean = suspendTransaction(db = database) {
        if (!repository.existsById(id)) {
            false
        } else {
            repository.deleteById(id)
            true
        }
    }
}
