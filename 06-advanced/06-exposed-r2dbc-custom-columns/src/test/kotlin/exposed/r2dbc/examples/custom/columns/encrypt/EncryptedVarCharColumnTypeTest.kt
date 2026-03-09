package exposed.r2dbc.examples.custom.columns.encrypt

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.crypto.encrypt.Encryptors
import io.bluetape4k.exposed.core.encrypt.encryptedVarChar
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 결정적(Deterministic) 암호화를 VARCHAR 컬럼에 적용하는 커스텀 컬럼 타입 예제.
 *
 * `bluetape4k-exposed` 의 `encryptedVarChar()` 확장 함수를 사용하여
 * INSERT 시 자동으로 데이터를 암호화하고, SELECT 시 자동으로 복호화합니다.
 * 결정적 암호화이므로 동일한 평문은 항상 동일한 암호문이 생성되어 WHERE 절 검색이 가능합니다.
 *
 * `exposed-crypt` 모듈의 [encryptedVarchar]과 달리, 결정적 암호화를 지원하므로 WHERE 절 필터링이 가능합니다.
 *
 * 지원 암호화 알고리즘 ([Encryptors]):
 * - [Encryptors.DeterministicAES]: AES 결정적 암호화
 * - [Encryptors.DeterministicRC4]: RC4 결정적 암호화
 * - [Encryptors.TripleDES]: Triple DES 암호화
 *
 * 내부 테이블 [T1]은 테스트 전용 private 스키마입니다.
 */
class EncryptedVarCharColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      "name" VARCHAR(50) NOT NULL,
     *      aes_str VARCHAR(1024) NULL,
     *      rc4_str VARCHAR(1024) NULL,
     *      triple_des_str VARCHAR(1024) NULL
     * );
     * ```
     *
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      `name` VARCHAR(50) NOT NULL,
     *      aes_str VARCHAR(1024) NULL,
     *      rc4_str VARCHAR(1024) NULL,
     *      triple_des_str VARCHAR(1024) NULL
     * );
     * ```
     */
    private object T1: IntIdTable("T1") {
        val name = varchar("name", 50)

        val aesString: Column<String?> = encryptedVarChar("aes_str", 1024, Encryptors.DeterministicAES).nullable()
        val rc4String: Column<String?> = encryptedVarChar("rc4_str", 1024, Encryptors.DeterministicRC4).nullable()
        val tripleDesString: Column<String?> =
            encryptedVarChar("triple_des_str", 1024, Encryptors.TripleDES).nullable()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `필드 값을을 암호화하여 VarChar 컬럼에 저장합니다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val text = Fakers.randomString(255, 512)

            val id = T1.insertAndGetId {
                it[T1.name] = "Encryption"

                it[aesString] = text
                it[rc4String] = text
                it[tripleDesString] = text
            }

            val row = T1.selectAll().where { T1.id eq id }.single()

            row[T1.name] shouldBeEqualTo "Encryption"
            row[T1.aesString] shouldBeEqualTo text
            row[T1.rc4String] shouldBeEqualTo text
            row[T1.tripleDesString] shouldBeEqualTo text
        }
    }

    /**
     * exposed-crypt 랑 달리 암호화된 값을 그대로 비교합니다.
     *
     * ```sql
     * -- Postgres
     * SELECT t1.id, t1."name", t1.aes_str, t1.rc4_str, t1.triple_des_str
     *   FROM t1
     *  WHERE t1.rc4_str = r4YnE6KiXJTMrB4S0qK-jmVXPKer7d1eJagtKd5LJX2DmOHiCo9LyrVYtZVXy2gG6lx47w1S2WC5vOkq;
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `암호화 컬럼으로 검색하기`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val text = "동해물과 백두산이 마르고 닳도록"

            val id = T1.insertAndGetId {
                it[T1.name] = "Encryption"

                it[aesString] = text
                it[rc4String] = text
                it[tripleDesString] = text
            }

            // exposed-crypt 랑 달리 암호화된 값을 그대로 비교합니다.
            val row = T1.selectAll().where { T1.rc4String eq text }.single()

            row[T1.name] shouldBeEqualTo "Encryption"
            row[T1.aesString] shouldBeEqualTo text
            row[T1.rc4String] shouldBeEqualTo text
            row[T1.tripleDesString] shouldBeEqualTo text
        }
    }
}
