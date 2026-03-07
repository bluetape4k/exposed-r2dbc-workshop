package exposed.r2dbc.shared.tests

import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.DatabaseDialectMetadata
import java.util.*

/**
 * 현재 트랜잭션 dialect의 식별자 규칙에 맞춰 문자열의 대소문자를 보정합니다.
 */
fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this) ?: this

/**
 * 현재 활성 트랜잭션의 dialect를 반환합니다.
 */
val currentDialectTest: DatabaseDialect
    get() = TransactionManager.current().db.dialect

/**
 * 현재 활성 트랜잭션의 dialect 메타데이터를 반환합니다.
 */
val currentDialectMetadataTest: DatabaseDialectMetadata
    get() = TransactionManager.current().db.dialectMetadata

/**
 * 활성 트랜잭션이 있으면 dialect를 반환하고, 없으면 `null`을 반환합니다.
 */
val currentDialectIfAvailableTest: DatabaseDialect?
    get() = TransactionManager.currentOrNull()?.db?.dialect

/**
 * 가독성 좋은 EnumSet 생성을 위한 유틸리티입니다.
 */
inline fun <reified E: Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> =
    elements.toCollection(EnumSet.noneOf(E::class.java))

/**
 * SQL Server 기본값 제약 이름이 필요할 때 제약명 조각을 생성합니다.
 */
fun <T> Column<T>.constraintNamePart() = (currentDialectTest as? SQLServerDialect)?.let {
    " CONSTRAINT DF_${table.tableName}_$name"
} ?: ""

/**
 * 빈 레코드를 추가한 뒤 즉시 커밋하고 지정한 시간만큼 지연합니다.
 *
 * 비동기/동시성 시나리오에서 커밋 이후 상태 전파를 검증할 때 사용합니다.
 */
suspend fun Table.insertAndSuspending(duration: Long) {
    this.insert { }
    TransactionManager.current().commit()
    delay(duration)
}
