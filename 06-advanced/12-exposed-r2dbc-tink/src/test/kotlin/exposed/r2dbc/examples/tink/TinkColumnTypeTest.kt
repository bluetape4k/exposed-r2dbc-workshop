package exposed.r2dbc.examples.tink

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.tink.tinkAeadBinary
import io.bluetape4k.exposed.core.tink.tinkAeadBlob
import io.bluetape4k.exposed.core.tink.tinkAeadVarChar
import io.bluetape4k.exposed.core.tink.tinkDaeadBinary
import io.bluetape4k.exposed.core.tink.tinkDaeadBlob
import io.bluetape4k.exposed.core.tink.tinkDaeadVarChar
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.support.toUtf8String
import io.bluetape4k.tink.aead.TinkAeads
import io.bluetape4k.tink.daead.TinkDaeads
import kotlinx.coroutines.flow.single
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertFailsWith

/**
 * Google Tink 기반 암호화 컬럼의 CRUD 동작을 검증한다.
 *
 * `bluetape4k-exposed` 의 Tink 확장 함수를 사용하여 Exposed 컬럼에 투명한 암복호화를 적용합니다.
 * Google Tink 는 업계 표준 암호화 알고리즘을 안전하게 사용할 수 있도록 설계된 고수준 암호화 라이브러리입니다.
 *
 * 제공 컬럼 타입:
 * - `tinkAeadVarChar` / `tinkAeadBinary` / `tinkAeadBlob`: AEAD(Authenticated Encryption with Associated Data)
 *   비결정적 암호화. 같은 평문도 매번 다른 암호문을 생성하므로 WHERE 조건 검색 불가.
 * - `tinkDaeadVarChar` / `tinkDaeadBinary` / `tinkDaeadBlob`: DAEAD(Deterministic AEAD)
 *   결정적 암호화. 같은 평문은 항상 같은 암호문을 생성하므로 WHERE 조건 검색 가능.
 *   `.index()` 와 함께 사용하여 인덱스 기반 조회를 지원합니다.
 *
 * 지원 알고리즘:
 * - AEAD: `TinkAeads.AES256_GCM`, `TinkAeads.AES128_GCM`, `TinkAeads.CHACHA20_POLY1305`
 * - DAEAD: `TinkDaeads.AES256_SIV`
 *
 * 검증 항목:
 * - AEAD 컬럼 삽입 및 조회 (VarChar, Binary, Blob)
 * - DAEAD 컬럼 삽입, 조회 및 WHERE 절 검색
 * - AEAD 컬럼 UPDATE
 * - nullable 암호화 컬럼의 null 값 보존
 * - 컬럼 길이 유효성 검사 (0 이하 길이 거부)
 * - 다양한 AEAD 알고리즘으로 멀티 컬럼 사용
 */
class TinkColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `AEAD 컬럼 암호화 및 복호화`(testDB: TestDB) = runSuspendIO {
        val secretTable = object: IntIdTable("tink_aead_table") {
            val secret = tinkAeadVarChar("secret", 512, TinkAeads.AES256_GCM).nullable()
            val data = tinkAeadBinary("data", 512, TinkAeads.AES256_GCM).nullable()
            val blob = tinkAeadBlob("blob", TinkAeads.AES256_GCM).nullable()
        }

        withTables(testDB, secretTable) {
            val insertedSecret = faker.lorem().sentence()
            val insertedData = faker.lorem().sentence()
            val insertedBlob = faker.lorem().sentence()

            val id = secretTable.insertAndGetId {
                it[secret] = insertedSecret
                it[data] = insertedData.toUtf8Bytes()
                it[blob] = insertedBlob.toUtf8Bytes()
            }

            secretTable.selectAll().count() shouldBeEqualTo 1L

            val row = secretTable.selectAll().where { secretTable.id eq id }.single()

            row[secretTable.secret] shouldBeEqualTo insertedSecret
            row[secretTable.data]!!.toUtf8String() shouldBeEqualTo insertedData
            row[secretTable.blob]!!.toUtf8String() shouldBeEqualTo insertedBlob
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DAEAD 컬럼 암호화, 복호화 및 조건 검색`(testDB: TestDB) = runSuspendIO {
        val searchableTable = object: IntIdTable("tink_daead_table") {
            val email = tinkDaeadVarChar("email", 512, TinkDaeads.AES256_SIV).nullable().index()
            val fingerprint = tinkDaeadBinary("fingerprint", 256, TinkDaeads.AES256_SIV).nullable()
            val blob = tinkDaeadBlob("blob", TinkDaeads.AES256_SIV).nullable()
        }

        withTables(testDB, searchableTable) {
            val insertedEmail = faker.internet().emailAddress()
            val insertedFingerprint = faker.lorem().word()
            val insertedBlob = faker.lorem().sentence()

            val id = searchableTable.insertAndGetId {
                it[email] = insertedEmail
                it[fingerprint] = insertedFingerprint.toUtf8Bytes()
                it[blob] = insertedBlob.toUtf8Bytes()
            }

            searchableTable.selectAll().count() shouldBeEqualTo 1L

            val row = searchableTable.selectAll().where { searchableTable.id eq id }.single()

            row[searchableTable.email] shouldBeEqualTo insertedEmail
            row[searchableTable.fingerprint]!!.toUtf8String() shouldBeEqualTo insertedFingerprint
            row[searchableTable.blob]!!.toUtf8String() shouldBeEqualTo insertedBlob


            /**
             * DAEAD(결정적 암호화)는 WHERE 절로 검색이 가능합니다.
             * ```sql
             * SELECT COUNT(*) FROM tink_daead_table WHERE tink_daead_table.email = '<암호문>'
             * ```
             */
            searchableTable.selectAll()
                .where { searchableTable.email eq row[searchableTable.email] }
                .count() shouldBeEqualTo 1L

            searchableTable.selectAll()
                .where { searchableTable.fingerprint eq row[searchableTable.fingerprint] }
                .count() shouldBeEqualTo 1L

            searchableTable.selectAll()
                .where { searchableTable.blob eq row[searchableTable.blob] }
                .count() shouldBeEqualTo 1L
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `AEAD 컬럼 Update`(testDB: TestDB) = runSuspendIO {
        val secretTable = object: IntIdTable("tink_aead_update_table") {
            val secret = tinkAeadVarChar("secret", 512, TinkAeads.AES256_GCM)
            val data = tinkAeadBinary("data", 512, TinkAeads.AES256_GCM).nullable()
            val blob = tinkAeadBlob("blob", TinkAeads.AES256_GCM).nullable()
        }

        withTables(testDB, secretTable) {
            val insertedSecret = faker.lorem().sentence()
            val insertedData = faker.lorem().word()
            val insertedBlob = faker.lorem().word()

            val id = secretTable.insertAndGetId {
                it[secret] = insertedSecret
                it[data] = insertedData.toUtf8Bytes()
                it[blob] = insertedBlob.toUtf8Bytes()
            }

            val insertedRow = secretTable.selectAll().where { secretTable.id eq id }.single()
            insertedRow[secretTable.secret] shouldBeEqualTo insertedSecret
            insertedRow[secretTable.data]!!.toUtf8String() shouldBeEqualTo insertedData
            insertedRow[secretTable.blob]!!.toUtf8String() shouldBeEqualTo insertedBlob


            val updatedSecret = faker.lorem().sentence()
            val updatedData = faker.lorem().word()
            val updatedBlob = faker.lorem().word()

            secretTable.update({ secretTable.id eq id }) {
                it[secret] = updatedSecret
                it[data] = updatedData.toUtf8Bytes()
                it[blob] = updatedBlob.toUtf8Bytes()
            }

            val updatedRow = secretTable.selectAll().where { secretTable.id eq id }.single()
            updatedRow[secretTable.secret] shouldBeEqualTo updatedSecret
            updatedRow[secretTable.data]!!.toUtf8String() shouldBeEqualTo updatedData
            updatedRow[secretTable.blob]!!.toUtf8String() shouldBeEqualTo updatedBlob
        }
    }

    /**
     * nullable 암호화 컬럼이 `null` 값을 그대로 보존하는지 검증한다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `nullable 암호화 컬럼은 null 값을 저장하고 조회할 수 있다`(testDB: TestDB) = runSuspendIO {
        val nullableTable = object: IntIdTable("tink_nullable_table") {
            val aeadSecret = tinkAeadVarChar("aead_secret", 512).nullable()
            val daeadSecret = tinkDaeadVarChar("daead_secret", 512).nullable()
            val aeadData = tinkAeadBinary("aead_data", 512).nullable()
            val daeadData = tinkDaeadBinary("daead_data", 512).nullable()
            val aeadBlob = tinkAeadBlob("aead_blob").nullable()
            val daeadBlob = tinkDaeadBlob("daead_blob").nullable()
        }

        withTables(testDB, nullableTable) {
            val id = nullableTable.insertAndGetId {
                it[aeadSecret] = null
                it[daeadSecret] = null
                it[aeadData] = null
                it[daeadData] = null
                it[aeadBlob] = null
                it[daeadBlob] = null
            }

            val row = nullableTable.selectAll().where { nullableTable.id eq id }.single()
            row[nullableTable.aeadSecret] shouldBeEqualTo null
            row[nullableTable.daeadSecret] shouldBeEqualTo null
            row[nullableTable.aeadData] shouldBeEqualTo null
            row[nullableTable.daeadData] shouldBeEqualTo null
            row[nullableTable.aeadBlob] shouldBeEqualTo null
            row[nullableTable.daeadBlob] shouldBeEqualTo null
        }
    }


    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `컬럼 길이는 0보다 커야 한다`(testDB: TestDB) = runSuspendIO {
        assertFailsWith<IllegalArgumentException> {
            object: IntIdTable("invalid_aead_varchar_$testDB") {
                val invalid = tinkAeadVarChar("invalid", 0)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            object: IntIdTable("invalid_aead_binary_$testDB") {
                val invalid = tinkAeadBinary("invalid", 0)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            object: IntIdTable("invalid_daead_varchar_$testDB") {
                val invalid = tinkDaeadVarChar("invalid", 0)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            object: IntIdTable("invalid_daead_binary_$testDB") {
                val invalid = tinkDaeadBinary("invalid", 0)
            }
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `다양한 Tink AEAD 알고리즘으로 암호화 컬럼을 사용할 수 있다`(testDB: TestDB) = runSuspendIO {
        val multiAlgoTable = object: IntIdTable("tink_multi_algo_table") {
            val aes256 = tinkAeadVarChar("aes256", 512, TinkAeads.AES256_GCM)
            val aes128 = tinkAeadVarChar("aes128", 512, TinkAeads.AES128_GCM)
            val chacha20 = tinkAeadVarChar("chacha20", 512, TinkAeads.CHACHA20_POLY1305)
        }

        withTables(testDB, multiAlgoTable) {
            val value = faker.lorem().sentence()

            val id = multiAlgoTable.insertAndGetId {
                it[aes256] = value
                it[aes128] = value
                it[chacha20] = value
            }

            val row = multiAlgoTable.selectAll().where { multiAlgoTable.id eq id }.single()

            row[multiAlgoTable.aes256] shouldBeEqualTo value
            row[multiAlgoTable.aes128] shouldBeEqualTo value
            row[multiAlgoTable.chacha20] shouldBeEqualTo value
        }
    }
}
