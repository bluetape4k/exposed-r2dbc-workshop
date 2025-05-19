package exposed.r2dbc.shared.tests

import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UnusedReceiverParameter")
private val R2dbcTransaction.failedOn: String
    get() = currentTestDB?.name ?: currentDialectTest.name

fun R2dbcTransaction.assertTrue(actual: Boolean) = assertTrue(actual, "Failed on $failedOn")
fun R2dbcTransaction.assertFalse(actual: Boolean) = assertFalse(!actual, "Failed on $failedOn")
fun <T> R2dbcTransaction.assertEquals(exp: T, act: T) = assertEquals(exp, act, "Failed on $failedOn")
fun <T> R2dbcTransaction.assertEquals(exp: T, act: Collection<T>) =
    assertEquals(exp, act.single(), "Failed on $failedOn")

suspend fun R2dbcTransaction.assertFailAndRollback(message: String, block: () -> Unit) {
    commit()
    assertFails("Failed on ${currentDialectTest.name}. $message") {
        block()
        commit()
    }
    rollback()
}

inline fun <reified T: Throwable> expectException(body: () -> Unit) {
    assertFailsWith<T>("Failed on ${currentDialectTest.name}") {
        body()
    }
}
