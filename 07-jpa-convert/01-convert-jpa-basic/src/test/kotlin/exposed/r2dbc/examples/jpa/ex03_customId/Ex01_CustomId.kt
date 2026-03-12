package exposed.r2dbc.examples.jpa.ex03_customId

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 사용자 정의 타입을 ID 또는 컬럼 타입으로 사용하는 예제.
 *
 * ## JPA `@Convert` → Exposed [ColumnWithTransform] 전환
 * JPA에서 `@Convert(converter = EmailConverter::class)`로 선언하던 커스텀 타입 변환을
 * Exposed에서는 [ColumnWithTransform] + [ColumnTransformer]로 대체합니다.
 *
 * ```
 * JPA                                         Exposed R2DBC
 * -------------------------------------------  -----------------------------------------
 * @EmbeddedId / @Id + @Convert                 IdTable<Email> + email("email_id").entityId()
 * AttributeConverter.convertToDatabaseColumn   ColumnTransformer.unwrap(value: Email): String
 * AttributeConverter.convertToEntityAttribute  ColumnTransformer.wrap(value: String): Email
 * ```
 *
 * ## 핵심 패턴
 * - [Email]을 PK로 사용할 경우 `IdTable<Email>`을 상속하고, `id` 컬럼을 커스텀 타입으로 정의합니다.
 * - [Ssn]처럼 PK가 아닌 일반 컬럼에도 동일한 [ColumnWithTransform] 패턴을 적용할 수 있습니다.
 * - Kotlin `value class`를 활용하면 DB 저장은 원시 타입으로, Kotlin 코드는 강타입으로 다룰 수 있습니다.
 *
 * @see CustomColumnTypes
 * @see Email
 * @see Ssn
 */
class Ex01_CustomId: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * Custom Type 의 Id 를 가지는 Table
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS emails (
     *      email_id VARCHAR(64) NOT NULL,
     *      "name" VARCHAR(255) NOT NULL,
     *      ssn CHAR(14) NOT NULL
     * );
     *
     * ALTER TABLE emails ADD CONSTRAINT emails_ssn_unique UNIQUE (ssn);
     * ```
     */
    object CustomIdTable: IdTable<Email>("emails") {
        override val id: Column<EntityID<Email>> = email("email_id").entityId()
        val name: Column<String> = varchar("name", 255)
        val ssn: Column<Ssn> = ssn("ssn").uniqueIndex()

        override val primaryKey = PrimaryKey(id)
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `create schema`(testDB: TestDB) = runTest {
        withDb(testDB) {
            SchemaUtils.create(CustomIdTable)
            CustomIdTable.exists().shouldBeTrue()
            SchemaUtils.drop(CustomIdTable)
        }
    }

    /**
     * Custom Type 의 Id 를 가지는 Table 에 레코드를 저장합니다.
     *
     * ```sql
     * INSERT INTO emails (email_id, "name", ssn)
     * VALUES (bud.lindgren@gmail.com, 'Prince Ziemann', 706-24-2397)
     * ```
     * ```sql
     * SELECT emails.email_id,
     *        emails."name",
     *        emails.ssn
     *   FROM emails
     *  WHERE emails.email_id = bud.lindgren@gmail.com
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL - save record`(testDB: TestDB) = runTest {
        withTables(testDB, CustomIdTable) {
            val entityId = CustomIdTable.insertAndGetId { row ->
                row[CustomIdTable.id] = Email(faker.internet().emailAddress())
                row[CustomIdTable.name] = faker.name().name()
                row[CustomIdTable.ssn] = Ssn(faker.idNumber().ssnValid())
            }
            entityId.value.value.shouldNotBeNull()

            val row = CustomIdTable.selectAll()
                .where { CustomIdTable.id eq entityId }
                .single()

            row[CustomIdTable.id].value shouldBeEqualTo entityId.value
        }
    }
}
