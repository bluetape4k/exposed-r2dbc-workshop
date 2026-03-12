package exposed.r2dbc.examples.jpa.ex01_simple

import io.bluetape4k.exposed.dao.entityToStringBuilder
import io.bluetape4k.exposed.dao.idEquals
import io.bluetape4k.exposed.dao.idHashCode
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import java.io.Serializable

/**
 * JPA → Exposed R2DBC 마이그레이션 예제 — 단순 엔티티 스키마
 *
 * ## JPA 대응 매핑
 * | JPA                         | Exposed R2DBC                         |
 * |-----------------------------|---------------------------------------|
 * | `@Entity` + `@Table`        | `object SimpleTable : LongIdTable(…)` |
 * | `@Id @GeneratedValue`       | `LongIdTable` (BIGSERIAL/AUTO_INCREMENT 자동 생성) |
 * | `@Column(nullable = false)` | `varchar(…)` (null 불허 기본값)       |
 * | `@Column(nullable = true)`  | `text(…).nullable()`                  |
 * | `EntityManager.persist()`   | `SimpleTable.insert { … }`            |
 * | `EntityManager.find()`      | `SimpleTable.selectAll().where { … }` |
 * | `@UniqueConstraint`         | `.uniqueIndex()`                      |
 * | DTO Projection (JPQL)       | `ResultRow.toSimpleRecord()` 확장 함수|
 *
 * ## 핵심 차이점
 * - JPA는 `EntityManager`를 통해 동기 방식으로 DB에 접근합니다.
 * - Exposed R2DBC는 `suspendTransaction` 내에서 비동기(코루틴) 방식으로 접근합니다.
 * - DAO 패턴: [LongEntity] + [LongEntityClass] 조합이 JPA의 `@Entity` + `Repository` 역할을 합니다.
 */
object SimpleSchema {

    /**
     * Table
     *
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS simple_entity (
     *      id BIGSERIAL PRIMARY KEY,
     *      "name" VARCHAR(255) NOT NULL,
     *      description TEXT NULL
     * );
     *
     * ALTER TABLE simple_entity
     *      ADD CONSTRAINT simple_entity_name_unique UNIQUE ("name");
     * ```
     */
    object SimpleTable: LongIdTable("simple_entity") {
        val name: Column<String> = varchar("name", 255).uniqueIndex()
        val description: Column<String?> = text("description").nullable()
    }

    /**
     * Entity
     */
    class SimpleEntity(id: EntityID<Long>): LongEntity(id) {
        companion object: LongEntityClass<SimpleEntity>(SimpleTable) {
            fun new(name: String): SimpleEntity {
                name.requireNotBlank("name")
                return SimpleEntity.new {
                    this.name = name
                }
            }
        }

        var name: String by SimpleTable.name
        var description: String? by SimpleTable.description

        override fun equals(other: Any?): Boolean = idEquals(other)
        override fun hashCode(): Int = idHashCode()
        override fun toString(): String = entityToStringBuilder()
            .add("name", name)
            .add("description", description)
            .toString()
    }

    /**
     * DTO
     */
    data class SimpleRecord(
        val id: Long,
        val name: String,
        val description: String?,
    ): Serializable {
        fun withId(id: Long) = copy(id = id)
    }

    fun SimpleEntity.toSimpleRecord(): SimpleRecord {
        return SimpleRecord(
            id = this.id.value,
            name = this.name,
            description = this.description
        )
    }

    fun ResultRow.toSimpleRecord(): SimpleRecord {
        return SimpleRecord(
            id = this[SimpleTable.id].value,
            name = this[SimpleTable.name],
            description = this[SimpleTable.description]
        )
    }

    suspend fun SizedIterable<ResultRow>.toSimpleRecords(): List<SimpleRecord> {
        return this.map { it.toSimpleRecord() }.toList()
    }
}
