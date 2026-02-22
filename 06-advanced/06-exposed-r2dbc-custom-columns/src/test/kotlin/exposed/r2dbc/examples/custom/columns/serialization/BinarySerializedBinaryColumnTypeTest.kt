package exposed.r2dbc.examples.custom.columns.serialization

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.serializable.binarySerializedBinary
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable

class BinarySerializedBinaryColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      "name" VARCHAR(50) NOT NULL,
     *      lz4_fury bytea NULL,
     *      zstd_fury bytea NULL,
     *      lz4_kryo bytea NULL,
     *      zstd_kryo bytea NULL
     * )
     * ```
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      `name` VARCHAR(50) NOT NULL,
     *      lz4_fury VARBINARY(4096) NULL,
     *      zstd_fury VARBINARY(4096) NULL,
     *      lz4_kryo VARBINARY(4096) NULL,
     *      zstd_kryo VARBINARY(4096) NULL
     * )
     * ```
     */
    private object T1: IntIdTable() {
        val name = varchar("name", 50)

        val lz4Fury = binarySerializedBinary<Embeddable>(
            "lz4_fury",
            4096,
            BinarySerializers.LZ4Fory
        ).nullable()

        val lz4Kryo = binarySerializedBinary<Embeddable>(
            "lz4_kryo",
            4096,
            BinarySerializers.LZ4Kryo
        ).nullable()

        val zstdFury = binarySerializedBinary<Embeddable2>(
            "zstd_fury",
            4096,
            BinarySerializers.ZstdFory
        ).nullable()

        val zstdKryo = binarySerializedBinary<Embeddable2>(
            "zstd_kryo",
            4096,
            BinarySerializers.ZstdKryo
        ).nullable()
    }

    data class Embeddable(
        val name: String,
        val age: Int,
        val address: String,
    ): Serializable

    data class Embeddable2(
        val name: String,
        val age: Int,
        val address: String,
        val zipcode: String,
    ): Serializable

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식으로 Object 를 Binary Serializer를 이용해 DB에 저장한다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val embedded = Embeddable("Alice", 20, "Seoul")
            val embedded2 = Embeddable2("John", 30, "Seoul", "12914")

            val id = T1.insertAndGetId {
                it[T1.name] = "Alice"

                it[lz4Fury] = embedded
                it[zstdFury] = embedded2
                it[lz4Kryo] = embedded
                it[zstdKryo] = embedded2
            }

            val row = T1.selectAll().where { T1.id eq id }.single()

            row[T1.id] shouldBeEqualTo id

            row[T1.lz4Fury] shouldBeEqualTo embedded
            row[T1.zstdFury] shouldBeEqualTo embedded2

            row[T1.lz4Kryo] shouldBeEqualTo embedded
            row[T1.zstdKryo] shouldBeEqualTo embedded2
        }
    }
}
