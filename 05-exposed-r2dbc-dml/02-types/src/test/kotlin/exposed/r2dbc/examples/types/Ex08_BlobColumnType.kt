package exposed.r2dbc.examples.types

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.expectException
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.toUtf8Bytes
import io.bluetape4k.support.toUtf8String
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.blobParam
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * `blob` 타입의 컬럼을 사용하는 방법
 *
 * @see [ExposedBlob]
 */
class Ex08_BlobColumnType: R2dbcExposedTestBase() {

    companion object: KLogging()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS test_blob (
     *      id SERIAL PRIMARY KEY,
     *      "content" bytea NOT NULL
     * )
     * ```
     */
    object BlobTable: IntIdTable("test_blob") {
        val content: Column<ExposedBlob> = blob("content")
    }

    /**
     * Test: Write and read blob value via alias
     *
     * ```sql
     * -- Postgres
     * INSERT INTO TEST_BLOB (CONTENT) VALUES (X'');
     *
     * SELECT TEST_BLOB.CONTENT content_column FROM TEST_BLOB;
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `write and read blob value via alias`(testDB: TestDB) = runTest {
        withTables(testDB, BlobTable) {
            val sampleData = Fakers.randomString(1024, 4096)
            BlobTable.insert {
                it[content] = ExposedBlob(sampleData.toUtf8Bytes())
            }

            val alias = BlobTable.content.alias("content_column")
            val content = BlobTable.select(alias).single()[alias].bytes.toUtf8String()
            content shouldBeEqualTo sampleData
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `test blob`(testDB: TestDB) = runTest {
        withTables(testDB, BlobTable) {
            val shortBytes = "Hello there!".toUtf8Bytes()
            val longBytes = Fakers.randomString(1024, 4096).toUtf8Bytes()
            val shortBlob = ExposedBlob(shortBytes)
            val longBlob = ExposedBlob(longBytes)

            val id1 = BlobTable.insert {
                it[content] = shortBlob
            } get BlobTable.id

            val id2 = BlobTable.insert {
                it[content] = longBlob
            } get BlobTable.id

            val id3 = BlobTable.insert {
                it[content] = blobParam(ExposedBlob(shortBytes))
            } get BlobTable.id

            val readOn1 = BlobTable.selectAll().where { BlobTable.id eq id1 }.first()[BlobTable.content]
            val text1 = readOn1.bytes.toUtf8String()
            val text2 = readOn1.inputStream.bufferedReader().readText()
            text1 shouldBeEqualTo "Hello there!"
            text2 shouldBeEqualTo "Hello there!"

            val readOn2 = BlobTable.selectAll().where { BlobTable.id eq id2 }.first()[BlobTable.content]
            val byte1 = readOn2.bytes
            val byte2 = readOn2.inputStream.readBytes()
            byte1 shouldBeEqualTo longBytes
            byte2 shouldBeEqualTo longBytes

            val byte3 = BlobTable.selectAll()
                .where { BlobTable.id eq id3 }
                .first()[BlobTable.content]
                .inputStream.readBytes()   // .bytes
            byte3 shouldBeEqualTo shortBytes
        }
    }

    /**
     * `blob` 타입의 컬럼에 기본값을 설정하는 방법. 단, MySQL 은 지원하지 않는다.
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `blob default`(testDB: TestDB) = runTest {
        val defaultBlobStr = "test"
        val defaultBlob = ExposedBlob(defaultBlobStr.toUtf8Bytes())

        /**
         * ```sql
         * -- Postgres
         * CREATE TABLE IF NOT EXISTS testtable (
         *      "number" INT NOT NULL,
         *      "blobWithDefault" bytea DEFAULT E'\\x74657374' NOT NULL
         * )
         * ```
         */
        val tester = object: Table("TestTable") {
            val number: Column<Int> = integer("number")
            val blobWithDefault: Column<ExposedBlob> = blob("blobWithDefault").default(defaultBlob)
        }

        withDb(testDB) {
            when (testDB) {
                // NOTE: MySQL 은 `blob` 컬럼의 기본값을 지원하지 않습니다.
                TestDB.MYSQL_V5, TestDB.MYSQL_V8 -> {
                    expectException<ExposedR2dbcException> {
                        SchemaUtils.create(tester)
                    }
                }

                else -> {
                    SchemaUtils.create(tester)

                    tester.insert {
                        it[number] = 1
                    }
                    tester.selectAll()
                        .first()[tester.blobWithDefault]
                        .bytes.toUtf8String() shouldBeEqualTo defaultBlobStr

                    SchemaUtils.drop(tester)
                }
            }
        }
    }

    // r2dbc-postgresql supports OID type, but only as a numeric (default is Integer) value;
    // so attempting to read/write expects a single identifier value;
    // there is no LargeObject functionality equivalent to JDBC setBlob() or getBlob();
    // there is only ByteBuffer or byte arrays, which is not a compatible mapping for encoding/decoding oid type.
    // Feature request: https://github.com/pgjdbc/r2dbc-postgresql/issues/255
//    /**
//     * Postgres 만 `useObjectIdentifier` 를 지원한다.
//     */
//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `blob as object identifier`(testDB: TestDB) = runTest {
//        /**
//         * ```sql
//         * -- Postgres
//         * CREATE TABLE IF NOT EXISTS blob_tester (
//         *      blob_col oid DEFAULT lo_from_bytea(0, E'\\x74657374' :: bytea) NOT NULL
//         * )
//         * ```
//         */
//        val defaultBytes = "test".toUtf8Bytes()
//        val defaultBlob = ExposedBlob(defaultBytes)
//        val tester = object: Table("blob_tester") {
//            val blobCol = blob("blob_col", useObjectIdentifier = true).default(defaultBlob)
//        }
//
//        withDb(testDB) {
//            if (currentDialectTest !is PostgreSQLDialect) {
//                expectException<IllegalStateException> {
//                    SchemaUtils.create(tester)
//                }
//            } else {
//                // object identifier 는 oid 타입을 사용한다.
//                tester.blobCol.descriptionDdl().split(" ")[1] shouldBeEqualTo "oid"
//                SchemaUtils.create(tester)
//
//                // INSERT INTO blob_tester  DEFAULT VALUES
//                tester.insert {}
//
//                val result1 = tester.selectAll().single()[tester.blobCol]
//                result1.bytes shouldBeEqualTo defaultBytes
//
//                tester.insert {
//                    defaultBlob.inputStream.reset()
//                    it[tester.blobCol] = defaultBlob
//                }
//                tester.insert {
//                    defaultBlob.inputStream.reset()
//                    it[tester.blobCol] = blobParam(defaultBlob, useObjectIdentifier = true)
//                }
//
//                val result2 = tester.selectAll().toList()
//                result2 shouldHaveSize 3
//                result2.all { it[tester.blobCol].bytes.contentEquals(defaultBytes) }.shouldBeTrue()
//
//                SchemaUtils.drop(tester)
//            }
//        }
//    }
}
