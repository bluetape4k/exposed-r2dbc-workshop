package exposed.r2dbc.examples.cache.ktor.domain

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Creates tables and inserts deterministic seed users for the example.
 */
class UserDataInitializer(
    private val database: R2dbcDatabase,
) {

    suspend fun initialize() {
        suspendTransaction(db = database) {
            SchemaUtils.create(UserTable)
            if (UserTable.selectAll().count() > 0) {
                return@suspendTransaction
            }
            UserTable.batchInsert(seedUsers) { user ->
                this[UserTable.username] = user.username
                this[UserTable.firstName] = user.firstName
                this[UserTable.lastName] = user.lastName
                this[UserTable.address] = user.address
                this[UserTable.zipcode] = user.zipcode
                this[UserTable.birthDate] = user.birthDate.toLocalDateOrNull()
            }
        }
    }

    private val seedUsers = listOf(
        UserRecord(username = "alice.cache", firstName = "Alice", lastName = "Cache", address = "Seoul", zipcode = "01001", birthDate = "1990-01-02"),
        UserRecord(username = "brad.cache", firstName = "Brad", lastName = "Reader", address = "Busan", zipcode = "48001", birthDate = "1988-03-04"),
        UserRecord(username = "cora.cache", firstName = "Cora", lastName = "Writer", address = "Incheon", zipcode = "22001", birthDate = "1992-05-06"),
    )
}
