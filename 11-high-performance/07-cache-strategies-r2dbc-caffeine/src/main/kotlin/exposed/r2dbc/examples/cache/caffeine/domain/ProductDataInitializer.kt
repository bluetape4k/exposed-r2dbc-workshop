package exposed.r2dbc.examples.cache.caffeine.domain

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** 상품 테이블을 만들고 반복 실행 가능한 두 행의 seed를 삽입합니다. */
class ProductDataInitializer(
    private val database: R2dbcDatabase,
) {

    suspend fun initialize() {
        suspendTransaction(db = database) {
            SchemaUtils.create(ProductTable)
            if (ProductTable.selectAll().count() > 0) return@suspendTransaction

            ProductTable.batchInsert(seedProducts) { product ->
                this[ProductTable.id] = product.sku
                this[ProductTable.name] = product.name
                this[ProductTable.version] = product.version
            }
        }
    }

    private val seedProducts = listOf(
        ProductRecord(sku = "sku-1", name = "Product 1", version = 1),
        ProductRecord(sku = "sku-2", name = "Product 2", version = 1),
    )
}
