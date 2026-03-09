package exposed.r2dbc.examples.custom.columns.encrypt

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.crypto.encrypt.Encryptors
import io.bluetape4k.exposed.core.encrypt.encryptedBinary
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.support.toUtf8String
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 결정적(Deterministic) 암호화를 BINARY 컬럼에 적용하는 커스텀 컬럼 타입 예제.
 *
 * `bluetape4k-exposed` 의 `encryptedBinary()` 확장 함수를 사용하여
 * INSERT 시 자동으로 바이너리 데이터를 암호화하고, SELECT 시 자동으로 복호화합니다.
 * 결정적 암호화이므로 암호화된 바이너리 값을 WHERE 절에서 직접 비교할 수 있습니다.
 *
 * [EncryptedVarCharColumnTypeTest] 와 달리 바이너리 형태로 저장하며,
 * PostgreSQL 에서는 `bytea`, MySQL 에서는 `VARBINARY` 타입을 사용합니다.
 *
 * 지원 암호화 알고리즘 ([Encryptors]):
 * - [Encryptors.DeterministicAES]: AES 결정적 암호화
 * - [Encryptors.DeterministicRC4]: RC4 결정적 암호화
 * - [Encryptors.TripleDES]: Triple DES 암호화
 *
 * 내부 테이블 [T1]은 테스트 전용 private 스키마입니다.
 */
class EncryptedBinaryColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      "name" VARCHAR(50) NOT NULL,
     *      aes_binary bytea NULL,
     *      rc4_binary bytea NULL,
     *      triple_des_binary bytea NULL
     * );
     * ```
     *
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      `name` VARCHAR(50) NOT NULL,
     *      aes_binary VARBINARY(1024) NULL,
     *      rc4_binary VARBINARY(1024) NULL,
     *      triple_des_binary VARBINARY(1024) NULL
     * );
     * ```
     */
    private object T1: IntIdTable("T1") {
        val name = varchar("name", 50)

        val aesBinary: Column<ByteArray?> = encryptedBinary("aes_binary", 1024, Encryptors.DeterministicAES).nullable()
        val rc4Binary: Column<ByteArray?> = encryptedBinary("rc4_binary", 1024, Encryptors.DeterministicRC4).nullable()
        val tripleDesBinary: Column<ByteArray?> =
            encryptedBinary("triple_des_binary", 1024, Encryptors.TripleDES).nullable()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `필드 값을을 암호화하여 Binary 컬럼에 저장합니다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val text = Fakers.randomString(255, 512)
            val bytes = text.toUtf8Bytes()

            val id = T1.insertAndGetId {
                it[T1.name] = "Encryption"

                it[aesBinary] = bytes
                it[rc4Binary] = bytes
                it[tripleDesBinary] = bytes
            }

            val row = T1.selectAll().where { T1.id eq id }.single()

            row[T1.name] shouldBeEqualTo "Encryption"
            row[T1.aesBinary]?.toUtf8String() shouldBeEqualTo text
            row[T1.rc4Binary]?.toUtf8String() shouldBeEqualTo text
            row[T1.tripleDesBinary]?.toUtf8String() shouldBeEqualTo text
        }
    }

    /**
     * exposed-crypt 랑 달리 암호화된 값을 그대로 비교합니다.
     *
     * ```sql
     * -- Postgres
     * SELECT t1.id, t1."name", t1.aes_binary, t1.rc4_binary, t1.triple_des_binary
     *   FROM t1
     *  WHERE t1.aes_binary = [B@6acb45c1
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식 - 암호화된 컬럼으로 검색합니다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val text = "동해물과 백두산이 마르고 닳도록"
            val bytes = text.toUtf8Bytes()

            T1.insert {
                it[T1.name] = "Encryption"

                it[aesBinary] = bytes
                it[rc4Binary] = bytes
                it[tripleDesBinary] = bytes
            }

            val row = T1.selectAll().where { T1.aesBinary eq bytes }.single()

            row[T1.name] shouldBeEqualTo "Encryption"
            row[T1.aesBinary]?.toUtf8String() shouldBeEqualTo text
            row[T1.rc4Binary]?.toUtf8String() shouldBeEqualTo text
            row[T1.tripleDesBinary]?.toUtf8String() shouldBeEqualTo text
        }
    }
}
