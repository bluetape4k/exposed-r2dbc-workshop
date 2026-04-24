package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.provider.Arguments

/**
 * 커스텀 ID 생성 전략을 가진 테이블 테스트의 공통 기반 클래스.
 *
 * MariaDB 계열은 분산 ID 생성 알고리즘의 충돌 가능성으로 인해 테스트에서 제외합니다.
 * [getTestDBAndEntityCount] 메서드를 통해 테스트 DB와 레코드 수 조합을 파라미터로 제공합니다.
 */
abstract class AbstractCustomIdTableTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel() {
        const val GET_TESTDB_AND_ENTITY_COUNT = "getTestDBAndEntityCount"

        @JvmStatic
        fun getTestDBAndEntityCount(): List<Arguments> {
            val recordCounts = listOf(50, 500)

            // enabled dialect가 전부 MariaDB 계열일 경우 JUnit 5가 빈 arguments를
            // `TemplateInvocationValidationException`로 처리하므로, 더미 인자 1개를 남겨
            // 테스트 본문의 Assumptions.assumeFalse 에서 스킵 처리하도록 한다.
            val filtered = TestDB.enabledDialects() - TestDB.ALL_MARIADB_LIKE
            val testDBs = filtered.ifEmpty { TestDB.enabledDialects() }

            return testDBs.map { testDB ->
                recordCounts.map { entityCount ->
                    Arguments.of(testDB, entityCount)
                }
            }.flatten()
        }
    }

    /**
     * MariaDB 계열 dialect 에서는 분산 ID 충돌 우려로 테스트를 스킵한다.
     * 모든 파라미터 테스트의 첫 줄에서 호출할 것.
     */
    protected fun skipIfMariaDB(testDB: TestDB) {
        Assumptions.assumeFalse { testDB in TestDB.ALL_MARIADB_LIKE }
    }

    @AfterEach
    fun afterEach() {
        Thread.sleep(10)
    }
}
