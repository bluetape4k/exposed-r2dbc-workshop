package exposed.r2dbc.examples.custom.columns.compress

import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.lzData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.snappyData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.zstdData
import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.compress.compressedBinary
import io.bluetape4k.io.compressor.Compressors
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.toUtf8String
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * 투명한 데이터 압축을 VARBINARY 컬럼에 적용하는 커스텀 컬럼 타입 예제.
 *
 * `bluetape4k-exposed` 의 `compressedBinary()` 확장 함수를 사용하여
 * INSERT 시 자동으로 데이터를 압축하고, SELECT 시 자동으로 압축을 해제합니다.
 *
 * 지원 압축 알고리즘:
 * - [Compressors.LZ4]: 빠른 압축/해제 속도 (일반 텍스트 데이터에 권장)
 * - [Compressors.Snappy]: Google Snappy - 빠른 속도와 적당한 압축률
 * - [Compressors.Zstd]: Zstandard - 높은 압축률과 빠른 속도
 *
 * 내부 테이블 [T1]은 테스트 전용 private 스키마이며, 컬럼명(예: `lzData`, `snappyData`, `zstdData`)은
 * 압축 알고리즘을 나타내는 약어입니다.
 */
class CompressedBinaryColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      lz4_data bytea NULL,
     *      snappy_data bytea NULL,
     *      zstd_data bytea NULL
     * )
     * ```
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      lz4_data VARBINARY(4096) NULL,
     *      snappy_data VARBINARY(4096) NULL,
     *      zstd_data VARBINARY(4096) NULL
     * )
     * ```
     */
    private object T1: IntIdTable() {
        val lzData: Column<ByteArray?> = compressedBinary("lz4_data", 4096, Compressors.LZ4).nullable()
        val snappyData: Column<ByteArray?> = compressedBinary("snappy_data", 4096, Compressors.Snappy).nullable()
        val zstdData: Column<ByteArray?> = compressedBinary("zstd_data", 4096, Compressors.Zstd).nullable()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식으로 데이터를 압축하여 byte array 컬럼에 저장합니다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val text = Fakers.randomString(1024, 2048)
            val bytes = text.toByteArray()

            val id = T1.insertAndGetId {
                it[lzData] = bytes
                it[snappyData] = bytes
                it[zstdData] = bytes
            }

            val row = T1.selectAll().where { T1.id eq id }.single()
            row[T1.lzData]?.toUtf8String() shouldBeEqualTo text
            row[T1.snappyData]?.toUtf8String() shouldBeEqualTo text
            row[T1.zstdData]?.toUtf8String() shouldBeEqualTo text
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식으로 null 값을 byte array 컬럼에 저장합니다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val id = T1.insertAndGetId {
                it[lzData] = null
                it[snappyData] = null
                it[zstdData] = null
            }

            val row = T1.selectAll().where { T1.id eq id }.single()
            row[lzData].shouldBeNull()
            row[snappyData].shouldBeNull()
            row[zstdData].shouldBeNull()
        }
    }
}
