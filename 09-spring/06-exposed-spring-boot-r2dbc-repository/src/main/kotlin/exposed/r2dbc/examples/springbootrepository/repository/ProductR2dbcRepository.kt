package exposed.r2dbc.examples.springbootrepository.repository

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.domain.Products
import io.bluetape4k.spring.data.exposed.r2dbc.repository.ExposedR2dbcRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * ProductRecord를 Exposed R2DBC provider repository에 연결하는 선언형 계약입니다.
 *
 * 구현 클래스와 개별 CRUD transaction은 provider proxy가 생성합니다.
 */
interface ProductR2dbcRepository : ExposedR2dbcRepository<ProductRecord, Long> {

    /** provider proxy가 사용할 Product table입니다. */
    override val table: IdTable<Long>
        get() = Products

    /** domain 객체에서 nullable ID를 추출합니다. */
    override fun extractId(entity: ProductRecord): Long? = entity.id

    /** Exposed ResultRow를 불변 ProductRecord로 변환합니다. */
    override fun toDomain(row: ResultRow): ProductRecord = ProductRecord(
        id = row[Products.id].value,
        name = row[Products.name],
        description = row[Products.description],
    )

    /** ProductRecord를 insert/update column 값으로 변환합니다. */
    override fun toPersistValues(domain: ProductRecord): Map<Column<*>, Any?> = buildMap {
        this[Products.name] = domain.name
        this[Products.description] = domain.description
    }
}
