package exposed.r2dbc.shared.tests

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class WithDbTest: AbstractR2dbcExposedTest() {

    @AfterEach
    fun tearDown() {
        registeredOnShutdown.remove(TestDB.H2)
        testDbSemaphores.remove(TestDB.H2)
        TestDB.H2.db = null
    }

    @Test
    fun `기본 설정에서는 기존 데이터베이스를 재사용한다`() = runTest {
        withDb(TestDB.H2) { }
        val registeredDb = TestDB.H2.db

        withDb(TestDB.H2) {
            assertSame(registeredDb, db)
        }

        assertSame(registeredDb, TestDB.H2.db)
    }
}
