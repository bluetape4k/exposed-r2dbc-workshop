package exposed.r2dbc.examples.jpa.ex02_entities

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

class Ex03_Task: R2dbcExposedTestBase() {

    companion object: KLogging()

    /**
     * TaskEntity 는 TaskTable 에 매핑된다.
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS TASKS (
     *      ID BIGINT AUTO_INCREMENT PRIMARY KEY,
     *      STATUS VARCHAR(10) NOT NULL,
     *      CHANGED_ON DATE NOT NULL,
     *      CHANGED_BY VARCHAR(255) NOT NULL
     * )
     * ```
     */
    object TaskTable: LongIdTable("tasks") {
        val status = enumerationByName("status", 10, TaskStatusType::class)
        val changedOn = date("changed_on")
        val changedBy = varchar("changed_by", 255)
    }

    enum class TaskStatusType {
        TO_DO,
        DONE,
        FAILED
    }

    private val today = LocalDate.now()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `create task entity with enum as string`(testDB: TestDB) = runTest {
        withTables(testDB, TaskTable) {
            val taskId = TaskTable.insertAndGetId {
                it[status] = TaskStatusType.TO_DO
                it[changedOn] = today
                it[changedBy] = "admin"
            }

            val row = TaskTable.selectAll().where { TaskTable.id eq taskId }.single()
            row[TaskTable.id] shouldBeEqualTo taskId

            row[TaskTable.status] shouldBeEqualTo TaskStatusType.TO_DO
            row[TaskTable.changedOn] shouldBeEqualTo today
            row[TaskTable.changedBy] shouldBeEqualTo "admin"
        }
    }
}
