package exposed.r2dbc.examples.custom.columns.compress

import exposed.r2dbc.examples.custom.columns.compress.CompressedBlobColumnTypeTest.T1.lzData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBlobColumnTypeTest.T1.snappyData
import exposed.r2dbc.examples.custom.columns.compress.CompressedBlobColumnTypeTest.T1.zstdData
import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.compress.compressedBlob
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

class CompressedBlobColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      lz4_blob bytea NULL,
     *      snappy_blob bytea NULL,
     *      zstd_blob bytea NULL
     * );
     *
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      lz4_blob BLOB NULL,
     *      snappy_blob BLOB NULL,
     *      zstd_blob BLOB NULL
     * );
     * ```
     */
    private object T1: IntIdTable() {
        val lzData: Column<ByteArray?> = compressedBlob("lz4_blob", Compressors.LZ4).nullable()
        val snappyData: Column<ByteArray?> = compressedBlob("snappy_blob", Compressors.Snappy).nullable()
        val zstdData: Column<ByteArray?> = compressedBlob("zstd_blob", Compressors.Zstd).nullable()
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식으로 데이터를 압축하여 blob 컬럼에 저장합니다`(testDB: TestDB) = runTest {
        val text = Fakers.randomString(2048, 4096)
        val bytes = text.toByteArray()

        withTables(testDB, T1) {
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
    fun `DSL 방식으로 null 값을 blob 컬럼에 저장합니다`(testDB: TestDB) = runTest {
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
