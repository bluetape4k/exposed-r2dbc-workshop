package exposed.r2dbc.sql.tests

import io.bluetape4k.junit5.coroutines.runSuspendTest
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test

class SampleSQL {

    companion object: KLogging()

    object Users: Table() {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = (integer("city_id") references Cities.id).nullable()

        override val primaryKey = PrimaryKey(id, name = "PK_User_ID") // name is optional here
    }

    object Cities: Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String> = varchar("name", 50)

        override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
    }

    @Test
    fun `r2dbc with H2`() = runSuspendTest {
        val database = R2dbcDatabase.connect("r2dbc:h2:mem:///test;USER=root;")

        suspendTransaction {
            SchemaUtils.create(Cities, Users)

            val saintPetersburgId = Cities.insert {
                it[name] = "St. Petersburg"
            } get Cities.id


        }
    }
}
