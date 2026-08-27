package exposed.r2dbc.examples.springbootrepository.support

import exposed.r2dbc.examples.springbootrepository.AbstractProductRepositoryTest
import exposed.r2dbc.examples.springbootrepository.domain.Products
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

/**
 * 애플리케이션 소유 database를 유지하면서 Product table만 격리하는 테스트 기반입니다.
 */
abstract class AbstractProductDatabaseTest : AbstractProductRepositoryTest() {

    @Autowired
    protected lateinit var database: R2dbcDatabase

    @BeforeEach
    fun resetProductFixtures() = runTest {
        ProductTestDatabaseLifecycle.reset(database)
    }

    @AfterEach
    fun cleanupProductFixtures() = runTest {
        ProductTestDatabaseLifecycle.cleanup(database)
    }
}

/**
 * hidden outer transaction을 만들지 않고 app-owned database에서 table lifecycle을 수행합니다.
 */
object ProductTestDatabaseLifecycle {

    suspend fun reset(database: R2dbcDatabase) {
        suspendTransaction(db = database) {
            SchemaUtils.drop(Products)
            SchemaUtils.create(Products)
            seedFixtures()
        }
    }

    suspend fun cleanup(database: R2dbcDatabase) {
        suspendTransaction(db = database) {
            SchemaUtils.drop(Products)
        }
    }

    private suspend fun org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction.seedFixtures() {
        Products.insert {
            it[name] = "Notebook"
            it[description] = "Fixture notebook"
        }
        Products.insert {
            it[name] = "Pen"
            it[description] = null
        }
    }
}
