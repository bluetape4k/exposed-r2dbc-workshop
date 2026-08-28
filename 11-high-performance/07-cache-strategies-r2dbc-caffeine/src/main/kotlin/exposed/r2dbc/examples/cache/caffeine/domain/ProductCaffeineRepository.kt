package exposed.r2dbc.examples.cache.caffeine.domain

import io.bluetape4k.exposed.cache.LocalCacheConfig
import io.bluetape4k.exposed.r2dbc.caffeine.repository.AbstractR2dbcCaffeineRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlinx.coroutines.flow.toList

/**
 * Exposed R2DBC Caffeine provider의 네 가지 mapping만 상품 모델에 연결합니다.
 * DB 접근과 write-behind lifecycle은 provider의 suspend 경계를 사용합니다.
 */
class ProductCaffeineRepository(
    config: LocalCacheConfig,
): AbstractR2dbcCaffeineRepository<String, ProductRecord>(config) {

    override val table: IdTable<String> = ProductTable

    override suspend fun ResultRow.toEntity(): ProductRecord = ProductRecord(
        sku = this[ProductTable.id].value,
        name = this[ProductTable.name],
        version = this[ProductTable.version],
    )

    override fun UpdateStatement.updateEntity(entity: ProductRecord) {
        this[ProductTable.name] = entity.name
        this[ProductTable.version] = entity.version
    }

    override fun BatchInsertStatement.insertEntity(entity: ProductRecord) {
        this[ProductTable.id] = entity.sku
        this[ProductTable.name] = entity.name
        this[ProductTable.version] = entity.version
    }

    override fun extractId(entity: ProductRecord): String = entity.sku

    /** 캐시를 건드리지 않고 상품 전체를 DB에서 읽습니다. */
    suspend fun findAllProductsFromDb(): List<ProductRecord> = suspendTransaction {
        ProductTable.selectAll()
            .toList()
            .map { row -> with(this@ProductCaffeineRepository) { row.toEntity() } }
    }
}
