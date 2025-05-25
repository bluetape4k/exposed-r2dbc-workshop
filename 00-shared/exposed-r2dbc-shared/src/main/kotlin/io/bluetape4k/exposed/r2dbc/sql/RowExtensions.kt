package io.bluetape4k.exposed.r2dbc.sql

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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.Query
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*


fun Row.getString(index: Int): String? = stringOrNull(index - 1)
fun Row.getString(label: String): String? = stringOrNull(label)

fun Row.getBoolean(index: Int): Boolean? = booleanOrNull(index - 1)
fun Row.getBoolean(label: String): Boolean? = booleanOrNull(label)

fun Row.getInt(index: Int): Int = intOrNull(index - 1) ?: 0
fun Row.getInt(label: String): Int = intOrNull(label) ?: 0

fun Row.getLong(index: Int): Long = longOrNull(index - 1) ?: 0L
fun Row.getLong(label: String): Long = longOrNull(label) ?: 0L

fun Row.getDouble(index: Int): Double? = doubleOrNull(index - 1)
fun Row.getDouble(label: String): Double? = doubleOrNull(label)

fun Row.getBigDecimal(index: Int): BigDecimal? = bigDecimalOrNull(index - 1)
fun Row.getBigDecimal(label: String): BigDecimal? = bigDecimalOrNull(label)

fun Row.getTimestamp(index: Int): Timestamp? = timestampOrNull(index - 1)
fun Row.getTimestamp(label: String): Timestamp? = timestampOrNull(label)

fun Row.getUUID(index: Int): UUID? = uuidOrNull(index - 1)
fun Row.getUUID(label: String): UUID? = uuidOrNull(label)

suspend fun Query.forEach(block: (ResultRow) -> Unit) {
    this.collect { row ->
        block(row)
    }
}

suspend fun Query.forEachIndexed(block: (Int, ResultRow) -> Unit) {
    this.collectIndexed { index, row ->
        block(index, row)
    }
}

suspend fun <T> Flow<T>.any(): Boolean {
    return this.firstOrNull() != null
}

suspend fun <T: Comparable<T>> Flow<T>.sorted(): List<T> = toList().sorted()

// 이건 뭔가 잘 못 되었다.
// suspend fun <T> Flow<T>.distinct(): List<T> = distinctUntilChanged().toList()
