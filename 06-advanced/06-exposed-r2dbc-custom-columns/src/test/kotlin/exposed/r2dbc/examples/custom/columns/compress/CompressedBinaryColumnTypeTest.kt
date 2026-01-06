package exposed.r2dbc.examples.custom.columns.compress

import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.lzData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.snappyData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBinaryColumnTypeTest.T1.zstdData
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.compress.compressedBinary
import io.bluetape4k.io.compressor.Compressors
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.KLogging
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

class CompressedBinaryColumnTypeTest: R2dbcExposedTestBase() {

    companion object: KLogging()

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
            row[T1.lzData]!!.toUtf8String() shouldBeEqualTo text
            row[T1.snappyData]!!.toUtf8String() shouldBeEqualTo text
            row[T1.zstdData]!!.toUtf8String() shouldBeEqualTo text
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
