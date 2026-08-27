package exposed.r2dbc.examples.springbootrepository.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Product 예제가 사용하는 단일 테이블입니다.
 */
object Products : LongIdTable("products") {
    /** Product 표시 이름이며 최대 120자입니다. */
    val name = varchar("name", 120).index()

    /** Product의 선택적 설명이며 최대 500자입니다. */
    val description = varchar("description", 500).nullable()
}
