package exposed.r2dbc.examples.jpa.ex03_customId

import org.jetbrains.exposed.v1.core.CharColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import java.io.Serializable

/**
 * Kotlin `value class`를 Exposed 컬럼 타입으로 사용하는 커스텀 타입 정의.
 *
 * ## JPA `@Convert` vs Exposed [ColumnWithTransform]
 * | JPA                              | Exposed R2DBC                                      |
 * |----------------------------------|----------------------------------------------------|
 * | `@Convert(converter = …)`        | `ColumnWithTransform<DB타입, 도메인타입>` 상속       |
 * | `AttributeConverter.convertToDB` | `ColumnTransformer.unwrap(value)` — 도메인 → DB     |
 * | `AttributeConverter.convertToEntity` | `ColumnTransformer.wrap(value)` — DB → 도메인   |
 * | `@Column(columnDefinition)`      | `registerColumn(name, CustomColumnType(…))`        |
 *
 * ## 사용 예
 * ```kotlin
 * object MyTable : Table() {
 *     val email = email("email_col")   // Column<Email>
 *     val ssn   = ssn("ssn_col")       // Column<Ssn>
 * }
 * ```
 * - DB에는 `VARCHAR` / `CHAR` 원시값으로 저장되고, Kotlin 코드에서는 강타입([Email], [Ssn])으로 다룰 수 있습니다.
 * - `value class`이므로 런타임 오버헤드가 없습니다.
 */
@JvmInline
value class Email(val value: String = EMPTY.value): Comparable<Email>, Serializable {
    companion object {
        val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        val EMPTY = Email("")
    }

    val isValid: Boolean
        get() = value.isNotEmpty() && emailRegex.matches(value)

    val isEmpty: Boolean
        get() = value.isEmpty()

    override fun compareTo(other: Email): Int = value.compareTo(other.value)
}

fun Table.email(name: String, length: Int = 64): Column<Email> =
    registerColumn(name, EmailColumnType(length))

open class EmailColumnType(length: Int = 64):
    ColumnWithTransform<String, Email>(VarCharColumnType(length), StringToEmailTransformer())

class StringToEmailTransformer: ColumnTransformer<String, Email> {
    override fun unwrap(value: Email): String = value.value
    override fun wrap(value: String): Email = Email(value)
}


@JvmInline
value class Ssn(val value: String): Serializable, Comparable<Ssn> {
    companion object {
        val ssnRegex = "^(\\d{6})(\\d{7})$".toRegex()
        val EMPTY = Ssn("")
        const val SSN_LENGTH = 14
    }

    val isValid: Boolean
        get() = value.isNotEmpty() && ssnRegex.matches(value)

    override fun compareTo(other: Ssn): Int = value.compareTo(other.value)
}

fun Table.ssn(name: String, length: Int = 14): Column<Ssn> =
    registerColumn(name, SsnColumnType(length))

open class SsnColumnType(
    length: Int = Ssn.SSN_LENGTH,
): ColumnWithTransform<String, Ssn>(CharColumnType(length), StringToSsnTransformer())

class StringToSsnTransformer: ColumnTransformer<String, Ssn> {
    override fun unwrap(value: Ssn): String = value.value
    override fun wrap(value: String): Ssn = Ssn(value)
}
