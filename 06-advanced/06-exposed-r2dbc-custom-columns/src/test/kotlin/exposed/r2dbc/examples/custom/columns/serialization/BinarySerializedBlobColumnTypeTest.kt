package exposed.r2dbc.examples.custom.columns.serialization

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.serializable.binarySerializedBlob
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

/**
 * Kotlin 객체를 이진 직렬화(Binary Serialization)하여 BLOB 컬럼에 저장하는 예제.
 *
 * `bluetape4k-exposed` 의 `binarySerializedBlob()` 확장 함수를 사용하여
 * INSERT 시 객체를 직렬화+압축하여 BLOB 컬럼에 저장하고, SELECT 시 자동으로 역직렬화합니다.
 * [BinarySerializedBinaryColumnTypeTest]와 달리 최대 크기 제한 없이 대용량 객체를 저장할 수 있습니다.
 *
 * 지원 직렬화+압축 조합 ([BinarySerializers]):
 * - [BinarySerializers.LZ4Fory]: LZ4 압축 + Fury 직렬화 (고성능)
 * - [BinarySerializers.LZ4Kryo]: LZ4 압축 + Kryo 직렬화
 * - [BinarySerializers.ZstdFory]: Zstd 압축 + Fury 직렬화 (높은 압축률)
 * - [BinarySerializers.ZstdKryo]: Zstd 압축 + Kryo 직렬화
 *
 * 내부 테이블 [T1]은 테스트 전용 private 스키마입니다.
 * [Embeddable2]는 스키마 진화(Schema Evolution)를 위해 `zipcode` 필드가 추가된 확장 모델입니다.
 */
class BinarySerializedBlobColumnTypeTest: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     *  CREATE TABLE IF NOT EXISTS t1 (
     *      id SERIAL PRIMARY KEY,
     *      "name" VARCHAR(50) NOT NULL,
     *      lz4_fury bytea NULL,
     *      lz4_kryo bytea NULL,
     *      zstd_fury bytea NULL,
     *      zstd_kryo bytea NULL
     * )
     * ```
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS T1 (
     *      id INT AUTO_INCREMENT PRIMARY KEY,
     *      `name` VARCHAR(50) NOT NULL,
     *      lz4_fury BLOB NULL,
     *      lz4_kryo BLOB NULL,
     *      zstd_fury BLOB NULL,
     *      zstd_kryo BLOB NULL
     * )
     * ```
     */
    private object T1: IntIdTable() {
        val name = varchar("name", 50)

        val lz4Fory = binarySerializedBlob<Embeddable>("lz4_fury", BinarySerializers.LZ4Fory).nullable()
        val lz4Kryo = binarySerializedBlob<Embeddable>("lz4_kryo", BinarySerializers.LZ4Kryo).nullable()
        val zstdFory = binarySerializedBlob<Embeddable2>("zstd_fury", BinarySerializers.ZstdFory).nullable()
        val zstdKryo = binarySerializedBlob<Embeddable2>("zstd_kryo", BinarySerializers.ZstdKryo).nullable()
    }

    data class Embeddable(
        val name: String,
        val age: Int,
        val address: String,
    ): Serializable

    // Schema evolution을 위해 추가된 필드
    data class Embeddable2(
        val name: String,
        val age: Int,
        val address: String,
        val zipcode: String,
    ): Serializable

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `DSL 방식으로 Object 를 Binary Serializer를 이용해 DB Blob에 저장한다`(testDB: TestDB) = runTest {
        withTables(testDB, T1) {
            val embedded = Embeddable("Alice", 20, "Seoul")
            val embedded2 = Embeddable2("John", 30, "Seoul", "12914")

            val id = T1.insertAndGetId {
                it[T1.name] = "Alice"

                it[lz4Fory] = embedded
                it[lz4Kryo] = embedded
                it[zstdFory] = embedded2
                it[zstdKryo] = embedded2
            }

            val row = T1.selectAll().where { T1.id eq id }.single()

            row[T1.id] shouldBeEqualTo id

            row[T1.lz4Fory] shouldBeEqualTo embedded
            row[T1.lz4Kryo] shouldBeEqualTo embedded
            row[T1.zstdFory] shouldBeEqualTo embedded2
            row[T1.zstdKryo] shouldBeEqualTo embedded2
        }
    }
}
