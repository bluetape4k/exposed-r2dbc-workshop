package exposed.r2dbc.examples.tink

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.tink.tinkAeadBinary
import io.bluetape4k.exposed.core.tink.tinkAeadVarChar
import io.bluetape4k.exposed.core.tink.tinkDaeadVarChar
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.support.toUtf8String
import io.bluetape4k.tink.aead.TinkAeads
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
 * Jasypt 기반 암호화 컬럼의 CRUD 동작을 검증한다.
 */
class TinkColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `문자열에 대해 암호화,복호화 하기`(testDB: TestDB) = runSuspendIO {
        val stringTable = object: IntIdTable("string_table") {
            val name = tinkDaeadVarChar("name", 255).nullable().index()
            val city = tinkDaeadVarChar("city", 255).nullable().index()
            val address = tinkAeadBinary("address", 255, TinkAeads.AES256_GCM).nullable()
            val age = tinkAeadVarChar("age", 255, TinkAeads.CHACHA20_POLY1305).nullable()
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
            val name = tinkDaeadVarChar("name", 255).index()
            val city = tinkDaeadVarChar("city", 255).index()
            val address = tinkAeadBinary("address", 255).nullable()
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
            val name = tinkDaeadVarChar("name", 255).nullable()
            val city = tinkDaeadVarChar("city", 255).nullable()
            val address = tinkAeadBinary("address", 255).nullable()
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
