package exposed.r2dbc.examples.connection.h2

import exposed.r2dbc.shared.dml.DMLTestData
import exposed.r2dbc.shared.samples.CountryTable
import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.exposed.r2dbc.getInt
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.invoke
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.name
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.Executors

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Ex02_H2_MultiDatabase {

    companion object: KLoggingChannel()

    private val db1 by lazy {
        R2dbcDatabase.connect(
            url = "r2dbc:h2:mem:///db1;USER=root;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }
    private val db2 by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db2;USER=root;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }

    private var currentDB: R2dbcDatabase? = null

    @BeforeEach
    fun beforeEach() {
        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }

        if (TransactionManager.isInitialized()) {
            currentDB = TransactionManager.currentOrNull()?.db
        }
    }

    @AfterEach
    fun afterEach() {
        // Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }
        TransactionManager.resetCurrent(currentDB?.transactionManager)
    }

    @Test
    fun `transaction with database`() = runTest {
        suspendTransaction(db = db1) {
            CountryTable.exists().shouldBeFalse()
            SchemaUtils.create(CountryTable)
            CountryTable.exists().shouldBeTrue()
            SchemaUtils.drop(CountryTable)
        }

        suspendTransaction(db = db2) {
            CountryTable.exists().shouldBeFalse()
            SchemaUtils.create(CountryTable)
            CountryTable.exists().shouldBeTrue()
            SchemaUtils.drop(CountryTable)
        }
    }

    @Test
    fun `simple insert in different databases`() = runTest {

        suspendTransaction(db = db1) {
            SchemaUtils.create(CountryTable)
            CountryTable.selectAll().empty().shouldBeTrue()
            CountryTable.insert {
                it[CountryTable.name] = "country1"
            }
        }

        suspendTransaction(db = db2) {
            SchemaUtils.create(CountryTable)
            CountryTable.selectAll().empty().shouldBeTrue()
            CountryTable.insert {
                it[CountryTable.name] = "country1"
            }
        }

        suspendTransaction(db = db1) {
            CountryTable.selectAll().count() shouldBeEqualTo 1L
            CountryTable.selectAll().single()[CountryTable.name] shouldBeEqualTo "country1"
            SchemaUtils.drop(CountryTable)
        }

        suspendTransaction(db = db2) {
            CountryTable.selectAll().count() shouldBeEqualTo 1L
            CountryTable.selectAll().single()[CountryTable.name] shouldBeEqualTo "country1"
            SchemaUtils.drop(CountryTable)
        }
    }

    @Test
    fun `Embedded Inserts In Different Database`() = runTest {
        suspendTransaction(db = db1) {
            SchemaUtils.drop(DMLTestData.Cities)
            SchemaUtils.create(DMLTestData.Cities)
            DMLTestData.Cities.selectAll().toList().shouldBeEmpty()
            DMLTestData.Cities.insert {
                it[DMLTestData.Cities.name] = "city1"
            }

            suspendTransaction(db = db2) {
                DMLTestData.Cities.exists().shouldBeFalse()
                SchemaUtils.create(DMLTestData.Cities)
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city2"
                }
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city3"
                }
                DMLTestData.Cities.selectAll().count().toInt() shouldBeEqualTo 2
                DMLTestData.Cities.selectAll().last()[DMLTestData.Cities.name] shouldBeEqualTo "city3"
                SchemaUtils.drop(DMLTestData.Cities)
            }

            DMLTestData.Cities.selectAll().count() shouldBeEqualTo 1L
            DMLTestData.Cities.selectAll().single()[DMLTestData.Cities.name] shouldBeEqualTo "city1"
            SchemaUtils.drop(DMLTestData.Cities)
        }
    }

    @Test
    fun `Embedded Inserts In Different Database Depth2`() = runTest {
        suspendTransaction(db = db1) {
            SchemaUtils.drop(DMLTestData.Cities)
            SchemaUtils.create(DMLTestData.Cities)
            DMLTestData.Cities.selectAll().empty().shouldBeTrue()
            DMLTestData.Cities.insert {
                it[DMLTestData.Cities.name] = "city1"
            }

            suspendTransaction(db = db2) {
                DMLTestData.Cities.exists().shouldBeFalse()
                SchemaUtils.create(DMLTestData.Cities)
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city2"
                }
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city3"
                }
                DMLTestData.Cities.selectAll().count().toInt() shouldBeEqualTo 2
                DMLTestData.Cities.selectAll().last()[DMLTestData.Cities.name] shouldBeEqualTo "city3"

                suspendTransaction(db = db1) {
                    DMLTestData.Cities.selectAll().count() shouldBeEqualTo 1L
                    DMLTestData.Cities.insert {
                        it[DMLTestData.Cities.name] = "city4"
                    }
                    DMLTestData.Cities.insert {
                        it[DMLTestData.Cities.name] = "city5"
                    }
                    DMLTestData.Cities.selectAll().count() shouldBeEqualTo 3L
                }

                DMLTestData.Cities.selectAll().count() shouldBeEqualTo 2L
                DMLTestData.Cities.selectAll().last()[DMLTestData.Cities.name] shouldBeEqualTo "city3"
                SchemaUtils.drop(DMLTestData.Cities)
            }

            DMLTestData.Cities.selectAll().count() shouldBeEqualTo 3L

            DMLTestData.Cities.selectAll()
                .map { it[DMLTestData.Cities.name] }
                .toList() shouldBeEqualTo listOf("city1", "city4", "city5")

            SchemaUtils.drop(DMLTestData.Cities)
        }
    }

    @Test
    fun `Coroutines With Multi Db`() = runTest {
        suspendTransaction(db = db1) {
            val trOuterId = this
            SchemaUtils.create(DMLTestData.Cities)
            DMLTestData.Cities.selectAll().empty().shouldBeTrue()
            DMLTestData.Cities.insert {
                it[DMLTestData.Cities.name] = "city1"
            }

            inTopLevelSuspendTransaction(
                transactionIsolation = db2.transactionManager.defaultIsolationLevel!!,
                db = db2
            ) {
                this.id shouldNotBeEqualTo trOuterId
                DMLTestData.Cities.exists().shouldBeFalse()
                SchemaUtils.create(DMLTestData.Cities)
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city2"
                }
                DMLTestData.Cities.insert {
                    it[DMLTestData.Cities.name] = "city3"
                }
                DMLTestData.Cities.selectAll().count().toInt() shouldBeEqualTo 2
                DMLTestData.Cities.selectAll().last()[DMLTestData.Cities.name] shouldBeEqualTo "city3"

                suspendTransaction(db1) {
                    DMLTestData.Cities.selectAll().count() shouldBeEqualTo 1L
                    DMLTestData.Cities.insert {
                        it[DMLTestData.Cities.name] = "city4"
                    }
                    DMLTestData.Cities.insert {
                        it[DMLTestData.Cities.name] = "city5"
                    }
                    DMLTestData.Cities.selectAll().count() shouldBeEqualTo 3L
                }

                DMLTestData.Cities.selectAll().count() shouldBeEqualTo 2L
                DMLTestData.Cities.selectAll().last()[DMLTestData.Cities.name] shouldBeEqualTo "city3"
                SchemaUtils.drop(DMLTestData.Cities)
            }

            DMLTestData.Cities.selectAll().count().toInt() shouldBeEqualTo 3
            DMLTestData.Cities.selectAll()
                .map {
                    it[DMLTestData.Cities.name]
                }
                .toList() shouldBeEqualTo listOf("city1", "city4", "city5")

            SchemaUtils.drop(DMLTestData.Cities)
        }
    }

    @Test
    fun `when default database is not explicitly set - should return the latest connection`() {
        db1
        db2
        db2 shouldBeEqualTo TransactionManager.defaultDatabase
    }

    @Test
    fun `when default database is explicitly set - should return the set connection`() {
        db1
        db2
        TransactionManager.defaultDatabase = db1
        db1 shouldBeEqualTo TransactionManager.defaultDatabase
        TransactionManager.defaultDatabase = null
    }

    @Test
    fun `when set default database is removed - should return the latest connection`() {
        db1
        db2
        TransactionManager.defaultDatabase = db1
        TransactionManager.closeAndUnregister(db1)
        db2 shouldBeEqualTo TransactionManager.defaultDatabase
        TransactionManager.defaultDatabase = null
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test // this test always fails for one reason or another
    fun `when the default database is changed, coroutines should respect that`(): Unit = runTest {
//        db1.name shouldBeEqualTo "jdbc:h2:mem:db1" // These two asserts fail sometimes for reasons that escape me
//        db2.name shouldBeEqualTo "jdbc:h2:mem:db2" // but if you run just these tests one at a time, they pass.

        val coroutineDispatcher1 = newSingleThreadContext("first")
        TransactionManager.defaultDatabase = db1
        withContext(coroutineDispatcher1) {
            suspendTransaction {
                TransactionManager.current().db.name shouldBeEqualTo db1.name
                // when running all tests together, this one usually fails
                // `Dual.select(intLiteral(1))`
                TransactionManager.current().exec("SELECT 1") { row ->
                    row.getInt(0) shouldBeEqualTo 1
                }
            }
        }
        TransactionManager.defaultDatabase = db2
        withContext(coroutineDispatcher1) {
            suspendTransaction {
                TransactionManager.current().db.name shouldBeEqualTo db2.name // fails??
                TransactionManager.current().exec("SELECT 1") { row ->
                    row.getInt(0) shouldBeEqualTo 1
                }
            }
        }
        TransactionManager.defaultDatabase = null
    }

    @Test // If the first two assertions pass, the entire test passes
    fun `when the default database is changed, threads should respect that`() = runTest {
        val threadpool = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        TransactionManager.defaultDatabase = db1
        threadpool.invoke {
            suspendTransaction {
                TransactionManager.current().db.name shouldBeEqualTo db1.name
                TransactionManager.current().exec("SELECT 1") { row ->
                    row.getInt(0) shouldBeEqualTo 1
                }
            }
        }

        TransactionManager.defaultDatabase = db2
        threadpool.invoke {
            suspendTransaction {
                TransactionManager.current().db.name shouldBeEqualTo db2.name
                TransactionManager.current().exec("SELECT 1") { row ->
                    row.getInt(0) shouldBeEqualTo 1
                }
            }
        }
        TransactionManager.defaultDatabase = null
    }
}
