package exposed.r2dbc.shared.tests

import io.bluetape4k.r2dbc.support.bigDecimalOrNull
import io.bluetape4k.r2dbc.support.booleanOrNull
import io.bluetape4k.r2dbc.support.doubleOrNull
import io.bluetape4k.r2dbc.support.intOrNull
import io.bluetape4k.r2dbc.support.longOrNull
import io.bluetape4k.r2dbc.support.stringOrNull
import io.bluetape4k.r2dbc.support.timestampOrNull
import io.bluetape4k.r2dbc.support.uuidOrNull
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.DatabaseDialectMetadata
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*

fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this) ?: this

val currentDialectTest: DatabaseDialect
    get() = TransactionManager.current().db.dialect

val currentDialectMetadataTest: DatabaseDialectMetadata
    get() = TransactionManager.current().db.dialectMetadata

val currentDialectIfAvailableTest: DatabaseDialect?
    get() =
        if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
            currentDialectTest
        } else {
            null
        }

inline fun <reified E: Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> =
    elements.toCollection(EnumSet.noneOf(E::class.java))

fun <T> Column<T>.constraintNamePart() = (currentDialectTest as? SQLServerDialect)?.let {
    " CONSTRAINT DF_${table.tableName}_$name"
} ?: ""

suspend fun Table.insertAndWait(duration: Long) {
    this.insert { }
    TransactionManager.current().commit()
    Thread.sleep(duration)
}

internal fun Row.getString(index: Int): String? = stringOrNull(index - 1)
internal fun Row.getString(label: String): String? = stringOrNull(label)

internal fun Row.getBoolean(index: Int): Boolean? = booleanOrNull(index - 1)
internal fun Row.getBoolean(label: String): Boolean? = booleanOrNull(label)

internal fun Row.getInt(index: Int): Int = intOrNull(index - 1) ?: 0
internal fun Row.getInt(label: String): Int = intOrNull(label) ?: 0

internal fun Row.getLong(index: Int): Long = longOrNull(index - 1) ?: 0L
internal fun Row.getLong(label: String): Long = longOrNull(label) ?: 0L

internal fun Row.getDouble(index: Int): Double? = doubleOrNull(index - 1)
internal fun Row.getDouble(label: String): Double? = doubleOrNull(label)

internal fun Row.getBigDecimal(index: Int): BigDecimal? = bigDecimalOrNull(index - 1)
internal fun Row.getBigDecimal(label: String): BigDecimal? = bigDecimalOrNull(label)

internal fun Row.getTimestamp(index: Int): Timestamp? = timestampOrNull(index - 1)
internal fun Row.getTimestamp(label: String): Timestamp? = timestampOrNull(label)

internal fun Row.getUUID(index: Int): UUID? = uuidOrNull(index - 1)
internal fun Row.getUUID(label: String): UUID? = uuidOrNull(label)

internal suspend fun Query.forEach(block: (ResultRow) -> Unit) {
    this.collect { row ->
        block(row)
    }
}

internal suspend fun Query.forEachIndexed(block: (Int, ResultRow) -> Unit) {
    this.collectIndexed { index, row ->
        block(index, row)
    }
}

internal suspend fun <T> Flow<T>.any(): Boolean {
    return this.firstOrNull() != null
}

internal suspend fun <T: Comparable<T>> Flow<T>.sorted(): List<T> = toList().sorted()

internal suspend fun <T> Flow<T>.distinct(): List<T> = distinctUntilChanged().toList()
