package exposed.r2dbc.examples.jasypt

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.crypto.encrypt.Encryptors
import io.bluetape4k.exposed.core.jasypt.jasyptBinary
import io.bluetape4k.exposed.core.jasypt.jasyptVarChar
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.support.toUtf8String
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Jasypt(Java Simplified Encryption) 기반 암호화 컬럼의 CRUD 동작을 검증한다.
 *
 * `bluetape4k-exposed` 의 `jasyptVarChar` / `jasyptBinary` 확장 함수를 사용하여
 * Exposed 컬럼에 투명한 암복호화를 적용합니다.
 *
 * Jasypt 는 결정적(Deterministic) 암호화와 비결정적(Non-Deterministic) 암호화를 모두 지원합니다:
 * - **결정적 암호화** (`Encryptors.DeterministicAES`, `Encryptors.DeterministicRC4`):
 *   같은 평문은 항상 같은 암호문을 생성하므로 WHERE 조건 검색 및 인덱스 사용이 가능합니다.
 * - **비결정적 암호화** (`Encryptors.TripleDES`, `Encryptors.RC2`):
 *   같은 평문도 매번 다른 암호문을 생성하므로 WHERE 조건 검색이 불가능합니다.
 *   (`assertFailsWith<AssertionError>` 로 검색 불가 특성을 검증합니다)
 *
 * 검증 항목:
 * - 문자열 암호화 / 복호화 (VarChar, Binary)
 * - WHERE 절 검색 가능 여부 (결정적 vs 비결정적)
 * - UPDATE 후 암호화 값 변경 및 복호화 확인
 * - nullable 암호화 컬럼에서 null 값 보존
 */
class JasyptColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `문자열에 대해 암호화,복호화 하기`(testDB: TestDB) = runSuspendIO {
        val stringTable = object: IntIdTable("string_table") {
            val name = jasyptVarChar("name", 255, Encryptors.DeterministicAES).nullable().index()
            val city = jasyptVarChar("city", 255, Encryptors.DeterministicRC4).nullable().index()
            val address = jasyptBinary("address", 255, Encryptors.TripleDES).nullable()
            val age = jasyptVarChar("age", 255, Encryptors.RC2).nullable()
        }

        withTables(testDB, stringTable) {
            val insertedName = faker.name().firstName()
            val insertedCity = faker.address().city()
            val insertedAddress = faker.address().fullAddress()
            val insertedAge = faker.number().numberBetween(18, 90).toString()

            val id = stringTable.insertAndGetId {
                it[name] = insertedName
                it[city] = insertedCity
                it[address] = insertedAddress.toUtf8Bytes()
                it[age] = insertedAge
            }

            stringTable.selectAll().count() shouldBeEqualTo 1L

            val row = stringTable.selectAll().where { stringTable.id eq id }.single()

            row[stringTable.name] shouldBeEqualTo insertedName
            row[stringTable.city] shouldBeEqualTo insertedCity
            row[stringTable.address]!!.toUtf8String() shouldBeEqualTo insertedAddress
            row[stringTable.age] shouldBeEqualTo insertedAge

            /**
             * Jasypt 암호화는 항상 같은 결과를 반환하므로, WHERE 절로 검색이 가능합니다.
             * ```sql
             * SELECT COUNT(*) FROM string_table WHERE string_table.`name` = UPq8X_QFkR-tsUFSOwffVQ==
             * ```
             */
            stringTable.selectAll()
                .where { stringTable.name eq row[stringTable.name] }
                .count() shouldBeEqualTo 1L

            /**
             * ```sql
             * SELECT COUNT(*) FROM string_table WHERE string_table.city = z4BcM_G7SfeezTVchpiz2U5PcOG8z88x
             * ```
             */
            stringTable.selectAll()
                .where { stringTable.city eq row[stringTable.city] }
                .count() shouldBeEqualTo 1L

            stringTable.selectAll()
                .where { stringTable.address eq row[stringTable.address] }
                .toList()
                .shouldBeEmpty()  // 비결정적 암호화 방식이라 검색이 안됩니다.

            stringTable.selectAll()
                .where { stringTable.age eq row[stringTable.age] }
                .toList()
                .shouldBeEmpty()  // 비결정적 암호화 방식이라 검색이 안됩니다.
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `암호화된 컬럼을 Update 하기`(testDB: TestDB) = runSuspendIO {
        val stringTable = object: IntIdTable("string_table") {
            val name = jasyptVarChar("name", 255, Encryptors.DeterministicAES).index()
            val city = jasyptVarChar("city", 255, Encryptors.DeterministicAES).index()
            val address = jasyptBinary("address", 255, Encryptors.TripleDES).nullable()
        }

        withTables(testDB, stringTable) {
            val insertedName = faker.name().firstName()
            val insertedCity = faker.address().city()
            val insertedAddress = faker.address().fullAddress()

            val id = stringTable.insertAndGetId {
                it[name] = insertedName
                it[city] = insertedCity
                it[address] = insertedAddress.toUtf8Bytes()
            }
            val insertedRow = stringTable.selectAll().where { stringTable.id eq id }.single()
            insertedRow[stringTable.name] shouldBeEqualTo insertedName
            insertedRow[stringTable.city] shouldBeEqualTo insertedCity
            insertedRow[stringTable.address]!!.toUtf8String() shouldBeEqualTo insertedAddress

            val updatedName = faker.name().firstName()
            val updatedCity = faker.address().city()
            val updatedAddress = faker.address().fullAddress()

            stringTable.update({ stringTable.id eq id }) {
                it[name] = updatedName
                it[city] = updatedCity
                it[address] = updatedAddress.toUtf8Bytes()
            }

            val updatedRow = stringTable.selectAll().where { stringTable.id eq id }.single()

            updatedRow[stringTable.name] shouldBeEqualTo updatedName
            updatedRow[stringTable.city] shouldBeEqualTo updatedCity
            updatedRow[stringTable.address]!!.toUtf8String() shouldBeEqualTo updatedAddress
        }
    }

    /**
     * nullable 암호화 컬럼이 `null` 값을 그대로 보존하는지 검증한다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `nullable encrypted columns keep null values`(testDB: TestDB) = runSuspendIO {
        val stringTable = object: IntIdTable("nullable_string_table") {
            val name = jasyptVarChar("name", 255, Encryptors.DeterministicAES).nullable()
            val city = jasyptVarChar("city", 255, Encryptors.DeterministicRC4).nullable()
            val address = jasyptBinary("address", 255, Encryptors.TripleDES).nullable()
        }

        withTables(testDB, stringTable) {
            val insertedName = faker.name().firstName()
            val id = stringTable.insertAndGetId {
                it[name] = insertedName
                it[city] = null
                it[address] = null
            }

            val row = stringTable.selectAll().where { stringTable.id eq id }.single()
            row[stringTable.name] shouldBeEqualTo insertedName
            row[stringTable.city].shouldBeNull()
            row[stringTable.address].shouldBeNull()
        }
    }
}
