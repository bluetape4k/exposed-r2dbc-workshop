package exposed.r2dbc.examples.dml

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.r2dbc.getInt
import io.bluetape4k.exposed.r2dbc.getIntOrNull
import io.bluetape4k.exposed.r2dbc.getString
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable

/**
 * Exposed R2DBC에서 재귀 CTE(Common Table Expression)를 사용하는 예제.
 *
 * ## 학습 내용
 *
 * - Raw SQL로 `WITH RECURSIVE` 절 작성
 * - 앵커 쿼리(anchor query)와 재귀 쿼리(recursive query)를 `UNION ALL`로 결합
 * - 계층형 데이터(카테고리 트리, 조직도 등) 탐색
 * - [org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.exec] 메서드로 결과 행을 매핑
 * - DB 방언(Dialect)별 SQL 차이: PostgreSQL vs MySQL/MariaDB
 *
 * ## 재귀 CTE 구조 (PostgreSQL 기준)
 *
 * ```sql
 * -- PostgreSQL
 * WITH RECURSIVE recursive_categories AS (
 *     -- 앵커 쿼리: 최상위 노드 선택
 *     SELECT id, parent_id, name, id::text AS path
 *     FROM categories
 *     WHERE parent_id IS NULL
 *
 *     UNION ALL
 *
 *     -- 재귀 쿼리: 자식 노드 반복 탐색
 *     SELECT c.id, c.parent_id, c.name, rc.path || '.' || c.id
 *     FROM categories c
 *     JOIN recursive_categories rc ON c.parent_id = rc.id
 *     WHERE POSITION('.' || c.id::text IN rc.path) = 0
 * )
 * SELECT * FROM recursive_categories;
 * ```
 *
 * ## 지원 DB
 *
 * | DB          | 재귀 CTE 지원 | 비고                        |
 * |-------------|------------|---------------------------|
 * | PostgreSQL  | O          | `id::text` 캐스팅 사용          |
 * | MySQL 8+    | O          | `CAST(id AS CHAR(255))` 사용 |
 * | MariaDB     | O          | MySQL과 동일한 문법              |
 * | H2          | X          | 이 예제에서는 제외                 |
 *
 * ## 주의사항
 *
 * - 무한 재귀를 방지하기 위해 종료 조건(`WHERE ... = 0`)을 반드시 포함해야 합니다.
 * - `StatementType.SELECT`를 명시해야 Exposed가 올바른 Statement 타입으로 처리합니다.
 * - 모든 쿼리는 `withTables(testDB, ...)` 블록 내에서 실행됩니다.
 */
class Ex50_RecursiveCTE: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    private val recursiveCTESupportedDb = TestDB.ALL_POSTGRES + TestDB.MYSQL_V8 + TestDB.MARIADB

    object Categories: IntIdTable("categories") {
        val parentId = integer("parent_id").nullable()
        val name = varchar("name", 50)
    }

    data class CategoryRecord(
        val id: Int,
        val parentId: Int?,
        val name: String,
        val path: String,
    ): Serializable

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `exec recursive cte`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB in recursiveCTESupportedDb }

        withTables(testDB, Categories) {
            val rootId = Categories.insertAndGetId {
                it[name] = "Root"
                it[parentId] = null
            }

            val child1Id1 = Categories.insertAndGetId {
                it[name] = "Child 1"
                it[parentId] = rootId.value
            }
            val child1Id2 = Categories.insertAndGetId {
                it[name] = "Child 2"
                it[parentId] = rootId.value
            }

            Categories.insert {
                it[name] = "Grandchild 1.1"
                it[parentId] = child1Id1.value
            }

            Categories.insert {
                it[name] = "Grandchild 1.2"
                it[parentId] = child1Id1.value
            }

            val sql = when (testDB) {
                in TestDB.ALL_POSTGRES                    -> categoriesWithRecursiveForPostgres()
                in setOf(TestDB.MYSQL_V8, TestDB.MARIADB) -> categoriesWithRecursiveForMySQL()
                else                                      -> throw IllegalStateException("Unsupported dialect for recursive CTE test: $testDB")
            }

            // val categoryRecords = mutableListOf<CategoryRecord>()
            val categoryRecords: List<CategoryRecord>? =
                exec(sql, explicitStatementType = StatementType.SELECT) { row ->
                    CategoryRecord(
                        id = row.getInt("id"),
                        parentId = row.getIntOrNull("parent_id"),
                        name = row.getString("name"),
                        path = row.getString("path")
                    ).apply {
                        log.debug { "Found category: $this" }
                    }
                }
                    ?.mapNotNull { it }
                    ?.toList()

            categoryRecords.shouldNotBeNull() shouldHaveSize 5
            categoryRecords[0].name shouldBeEqualTo "Root"
            categoryRecords[1].name shouldBeEqualTo "Child 1"
            categoryRecords[2].name shouldBeEqualTo "Child 2"
            categoryRecords[3].name shouldBeEqualTo "Grandchild 1.1"
            categoryRecords[4].name shouldBeEqualTo "Grandchild 1.2"
        }
    }

    private fun categoriesWithRecursiveForPostgres(): String = """
        WITH RECURSIVE recursive_categories AS (
            SELECT
                id,
                parent_id,
                name,
                id::text AS path
            FROM categories
            WHERE parent_id IS NULL
        
            UNION ALL
        
            SELECT
                c.id,
                c.parent_id,
                c.name,
                rc.path || '.' || c.id
            FROM categories c
            JOIN recursive_categories rc ON c.parent_id = rc.id
            WHERE POSITION('.' || c.id::text IN rc.path) = 0
        )
        SELECT *
        FROM recursive_categories;
    """.trimIndent()

    private fun categoriesWithRecursiveForMySQL(): String = """
        WITH RECURSIVE recursive_categories AS (
            SELECT
                id,
                parent_id,
                name,
                CAST(id AS CHAR(255)) AS path
            FROM categories
            WHERE parent_id IS NULL
        
            UNION ALL
        
            SELECT
                c.id,
                c.parent_id,
                c.name,
                CONCAT(rc.path, '.', c.id)
            FROM categories c
            JOIN recursive_categories rc ON c.parent_id = rc.id
            WHERE LOCATE(CONCAT('.', c.id), rc.path) = 0
        )
        SELECT *
        FROM recursive_categories;
    """.trimIndent()
}
