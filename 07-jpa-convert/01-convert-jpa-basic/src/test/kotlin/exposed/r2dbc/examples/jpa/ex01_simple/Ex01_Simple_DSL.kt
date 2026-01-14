package exposed.r2dbc.examples.jpa.ex01_simple

import exposed.r2dbc.examples.jpa.ex01_simple.SimpleSchema.SimpleTable
import exposed.r2dbc.examples.jpa.ex01_simple.SimpleSchema.toSimpleDTOs
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * [SimpleTable] 을 DSL 을 이용하여 작업하는 예제
 */
class Ex01_Simple_DSL: R2dbcExposedTestBase() {

    companion object: KLoggingChannel() {
        private const val ENTITY_COUNT = 10
    }

    @Suppress("UnusedReceiverParameter")
    private suspend fun R2dbcTransaction.persistSimpleEntities() {
        val names = List(ENTITY_COUNT) { faker.name().name() }

        SimpleTable.batchInsert(names) { name ->
            this[SimpleTable.name] = name
            this[SimpleTable.description] = faker.lorem().sentence()
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `find by names`(testDB: TestDB) = runTest {
        withTables(testDB, SimpleTable) {

            persistSimpleEntities()

            /**
             * ```sql
             * SELECT simple_entity."name"
             *   FROM simple_entity
             *  LIMIT 2
             *  OFFSET 2
             * ```
             */
            val names: List<String> = SimpleTable
                .select(SimpleTable.name)
                .limit(2)
                .offset(2)
                .map { it[SimpleTable.name] }
                .toList()

            /**
             * ```sql
             * SELECT simple_entity.id,
             *        simple_entity."name",
             *        simple_entity.description
             *   FROM simple_entity
             *  WHERE simple_entity."name" IN ('Ms. Cyril Doyle', 'Byron Hermiston Sr.')
             * ```
             */
            val rowCount = SimpleTable.selectAll()
                .where { SimpleTable.name inList names }
                .count()

            rowCount.toInt() shouldBeEqualTo names.size
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `projection DTO`(testDB: TestDB) = runTest {
        withTables(testDB, SimpleTable) {
            persistSimpleEntities()

            val query = SimpleTable.selectAll()

            // ResultRow 를 DTO 로 만든다.
            val dtos = query.toSimpleDTOs()
            dtos.forEach { dto ->
                log.debug { "DTO=$dto" }
            }
            dtos shouldHaveSize ENTITY_COUNT
        }
    }
}
