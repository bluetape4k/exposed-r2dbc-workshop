package exposed.r2dbc.examples.dml

import exposed.r2dbc.examples.dml.Ex22_ColumnWithTransform.TransformTable.simple
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.idgenerators.uuid.TimebasedUuid
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable
import java.util.*

/**
 * 컬럼의 수형이나 값을 변환하는 [ColumnTransformer] 를 활용하는 예제
 */
class Ex22_ColumnWithTransform: R2dbcExposedTestBase() {

    companion object: KLogging()

    @JvmInline
    value class Holder(val value: Int): Serializable

    // Holder <-> Int 변환을 수행하는 [ColumnTransformer] 구현
    class HolderTransformer: ColumnTransformer<Int, Holder> {
        override fun unwrap(value: Holder): Int = value.value
        override fun wrap(value: Int): Holder = Holder(value)
    }

    // Holder? <-> Int? 변환을 수행하는 [ColumnTransformer] 구현
    class HolderNullableTransformer: ColumnTransformer<Int?, Holder?> {
        override fun unwrap(value: Holder?): Int? = value?.value
        override fun wrap(value: Int?): Holder? = value?.let { Holder(it) }
    }

    // Holder? <-> Int 변환을 수행하는 [ColumnTransformer] 구현
    class HolderNullTransformer: ColumnTransformer<Int, Holder?> {
        override fun unwrap(value: Holder?): Int = value?.value ?: 0
        override fun wrap(value: Int): Holder? = if (value == 0) null else Holder(value)
    }

    /**
     * 회귀적으로 transform을 적용하는 경우, ColumnWithTransform의 수형이 변환된 수형이어야 한다.
     *
     * 예를 들어, Binary Serializer를 적용한 후, Compressor 를 적용하는 경우 등이 있다.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `recursive unwrap`() = runTest {
        val tester1 = object: IntIdTable() {
            val value: Column<Holder?> = integer("value")
                .transform(HolderTransformer())
                .nullable()
        }
        val columnType1 = tester1.value.columnType as? ColumnWithTransform<Int, Holder>
        columnType1.shouldNotBeNull()
        columnType1.unwrapRecursive(Holder(1)) shouldBeEqualTo 1
        columnType1.unwrapRecursive(null).shouldBeNull()

        // Transform null into non-null value
        val tester2 = object: IntIdTable() {
            val value = integer("value")
                .nullTransform(HolderNullTransformer())
        }
        val columnType2 = tester2.value.columnType as? ColumnWithTransform<Int, Holder?>
        columnType2.shouldNotBeNull()
        columnType2.unwrapRecursive(Holder(1)) shouldBeEqualTo 1
        columnType2.unwrapRecursive(null) shouldBeEqualTo 0

        // Transform 을 2번 적용하므로, ColumnWithTransform<Holder?, Int?> 가 생성되어야 한다.
        val tester3 = object: IntIdTable() {
            val value = integer("value")
                .transform(HolderTransformer())
                .nullable()
                .transform(wrap = { it?.value ?: 0 }, unwrap = { Holder(it ?: 0) })
        }
        val columnType3 = tester3.value.columnType as? ColumnWithTransform<Holder?, Int?>
        columnType3.shouldNotBeNull()
        columnType3.unwrapRecursive(1) shouldBeEqualTo 1
        columnType3.unwrapRecursive(null) shouldBeEqualTo 0
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `simple transforms`(testDB: TestDB) = runTest {
        /**
         * `transform` 함수를 이용해 wrapping, unwrapping을 수행하는 예제
         *
         * ```sql
         * CREATE TABLE IF NOT EXISTS simple_transforms (
         *      id SERIAL PRIMARY KEY,
         *      v1 INT NOT NULL,
         *      v2 INT NULL,
         *      v3 INT NULL
         * )
         * ```
         */
        val tester = object: IntIdTable("simple_transforms") {
            val v1 = integer("v1")
                .transform(
                    wrap = { Holder(it) },
                    unwrap = { it.value }
                )
            val v2 = integer("v2")
                .nullable()
                .transform(
                    wrap = { it?.let { Holder(it) } },
                    unwrap = { it?.value }
                )
            val v3 = integer("v3")
                .transform(
                    wrap = { Holder(it) },
                    unwrap = { it.value }
                )
                .nullable()
        }

        withTables(testDB, tester) {
            // INSERT INTO simple_transforms (v1, v2, v3) VALUES (1, 2, 3)
            val id1 = tester.insertAndGetId {
                it[v1] = Holder(1)
                it[v2] = Holder(2)
                it[v3] = Holder(3)
            }

            val entry1 = tester.selectAll().where { tester.id eq id1 }.single()
            entry1[tester.v1].value shouldBeEqualTo 1
            entry1[tester.v2]?.value shouldBeEqualTo 2
            entry1[tester.v3]?.value shouldBeEqualTo 3

            // INSERT INTO simple_transforms (v1, v2, v3) VALUES (1, NULL, NULL)
            val id2 = tester.insertAndGetId {
                it[v1] = Holder(1)
                it[v2] = null
                it[v3] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.single()
            entry2[tester.v1].value shouldBeEqualTo 1
            entry2[tester.v2].shouldBeNull()
            entry2[tester.v3].shouldBeNull()
        }
    }

    /**
     * 여러 개의 `transform` 을 중첩해서 적용하는 예
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `nested transforms`(testDB: TestDB) = runTest {
        /**
         * Postgres:
         * ```sql
         * CREATE TABLE IF NOT EXISTS nested_transformer (
         *      id SERIAL PRIMARY KEY,
         *      v1 INT NOT NULL,
         *      v2 INT NULL,
         *      v3 INT NULL,
         *      v4 INT NULL
         * )
         * ```
         */
        val tester = object: IntIdTable("nested_transformer") {
            val v1: Column<String> = integer("v1")
                .transform(HolderTransformer())
                .transform(
                    wrap = { it.value.toString() },
                    unwrap = { Holder(it.toInt()) }
                )

            val v2: Column<String?> = integer("v2")
                .transform(HolderTransformer())
                .transform(
                    wrap = { it.value.toString() },
                    unwrap = { Holder(it.toInt()) }
                )
                .nullable()

            val v3: Column<String?> = integer("v3")
                .transform(HolderTransformer())
                .nullable()
                .transform(
                    wrap = { it?.value.toString() },
                    unwrap = { it?.let { it1 -> Holder(it1.toInt()) } }
                )

            val v4: Column<String?> = integer("v4")
                .nullable()
                .transform(HolderNullableTransformer())
                .transform(
                    wrap = { it?.value.toString() },
                    unwrap = { it?.let { it1 -> Holder(it1.toInt()) } }
                )
        }

        withTables(testDB, tester) {
            val id1 = tester.insertAndGetId {
                it[v1] = "1"
                it[v2] = "2"
                it[v3] = "3"
                it[v4] = "4"
            }

            val entry1 = tester.selectAll().where { tester.id eq id1 }.single()
            entry1[tester.v1] shouldBeEqualTo "1"
            entry1[tester.v2] shouldBeEqualTo "2"
            entry1[tester.v3] shouldBeEqualTo "3"
            entry1[tester.v4] shouldBeEqualTo "4"

            val id2 = tester.insertAndGetId {
                it[v1] = "1"
                it[v2] = null
                it[v3] = null
                it[v4] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.single()
            entry2[tester.v1] shouldBeEqualTo "1"
            entry2[tester.v2].shouldBeNull()
            entry2[tester.v3].shouldBeNull()
            entry2[tester.v4].shouldBeNull()
        }
    }

    /**
     * [InsertStatement] 에서 transform 된 값을 읽어오는 예제
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `read transformed values from insert statement`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("read_transformed_values") {
            val v1: Column<Holder> = integer("v1").transform(HolderTransformer())
            val v2: Column<Holder?> = integer("v2").nullTransform(HolderNullTransformer())
        }

        withTables(testDB, tester) {
            /**
             * ```sql
             * INSERT INTO read_transformed_values (v1, v2) VALUES (1, 0)
             * ```
             */
            val statement = tester.insert {
                it[tester.v1] = Holder(1)
                it[tester.v2] = null
            }

            statement[tester.v1] shouldBeEqualTo Holder(1)
            statement[tester.v2].shouldBeNull()
        }
    }

    /**
     * ```sql
     * CREATE TABLE IF NOT EXISTS TRANSFORM_TABLE (
     *      ID INT AUTO_INCREMENT PRIMARY KEY,
     *      "simple" INT DEFAULT 1 NOT NULL,
     *      CHAINED VARCHAR(128) DEFAULT '2' NOT NULL
     * )
     * ```
     */
    object TransformTable: IntIdTable("transform_table") {
        val simple: Column<Holder> = integer("simple")
            .default(1)
            .transform(HolderTransformer())

        val chained: Column<Holder> = varchar("chained", 128)
            .transform(wrap = { it.toInt() }, unwrap = { it.toString() })
            .transform(HolderTransformer())
            .default(Holder(2))
    }

//    class TransformEntity(id: EntityID<Int>): IntEntity(id) {
//        companion object: IntEntityClass<TransformEntity>(TransformTable)
//
//        var simple: Holder by TransformTable.simple
//        var chained: Holder by TransformTable.chained
//
//        override fun equals(other: Any?): Boolean = idEquals(other)
//        override fun hashCode(): Int = idHashCode()
//        override fun toString(): String = toStringBuilder()
//            .add("simple", simple)
//            .add("chained", chained)
//            .toString()
//    }

//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `transformed values with DAO`(testDB: TestDB) = runTest {
//        withTables(testDB, TransformTable) {
//            // DAO 방식
//            val entity = TransformEntity.new {
//                simple = Holder(120)
//                chained = Holder(240)
//            }
//            log.debug { "entity: $entity" }
//            entity.simple shouldBeEqualTo Holder(120)
//            entity.chained shouldBeEqualTo Holder(240)
//
//            // SQL DSL 방식
//            val row = TransformTable.selectAll().first()
//            row[TransformTable.simple] shouldBeEqualTo Holder(120)
//            row[TransformTable.chained] shouldBeEqualTo Holder(240)
//        }
//    }
//
//    @ParameterizedTest
//    @MethodSource(ENABLE_DIALECTS_METHOD)
//    fun `entity with default value`(testDB: TestDB) = runTest {
//        withTables(testDB, TransformTable) {
//            val entity = TransformEntity.new { }
//            entity.simple shouldBeEqualTo Holder(1)
//            entity.chained shouldBeEqualTo Holder(2)
//
//            val row = TransformTable.selectAll().first()
//            row[TransformTable.simple] shouldBeEqualTo Holder(1)
//            row[TransformTable.chained] shouldBeEqualTo Holder(2)
//        }
//    }

    @JvmInline
    value class CustomId(val id: UUID): Serializable

    /**
     * value class 를 entity id 로 사용하는 예제 (`transform` 함수를 이용해 wrapping, unwrapping을 수행)
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transform id column`(testDB: TestDB) = runTest {

        /**
         * value class인 [CustomId]의 value 수형이 UUID 이므로, 테이블 기본 키의 수형은 UUID 이다.
         * ```sql
         * -- Postgres
         * CREATE TABLE IF NOT EXISTS tester (id uuid PRIMARY KEY)
         * ```
         */
        val tester = object: IdTable<CustomId>("tester") {
            override val id: Column<EntityID<CustomId>> = uuid("id")
                .transform(wrap = { CustomId(it) }, unwrap = { it.id })  // value class 는 이렇게 사용하면 된다.
                .entityId()

            override val primaryKey = PrimaryKey(id)
        }

        /**
         * `reference` 컬럼은 `tester` 테이블의 기본 키를 참조한다. 같은 수형인 UUID 수형으로 정의됩니다.
         *
         * ```sql
         * CREATE TABLE IF NOT EXISTS ref_tester (
         *      id SERIAL PRIMARY KEY,
         *      reference uuid NOT NULL,
         *
         *      CONSTRAINT fk_ref_tester_reference__id FOREIGN KEY (reference) REFERENCES tester(id)
         *          ON DELETE RESTRICT ON UPDATE RESTRICT
         * )
         * ```
         */
        val referenceTester = object: IntIdTable("ref_tester") {
            val reference: Column<EntityID<CustomId>> = reference("reference", tester)
        }

        val uuid = TimebasedUuid.Epoch.nextId()
        withTables(testDB, tester, referenceTester) {
            // CustomId 를 지정 (UUID 값만 저장됨)
            /**
             * ```sql
             * INSERT INTO tester (id) VALUES ('${uuid}')
             * ```
             */
            tester.insert {
                it[id] = CustomId(uuid)
            }
            val transformedId: EntityID<CustomId> = tester.selectAll().single()[tester.id]
            transformedId.value shouldBeEqualTo CustomId(uuid)

            /**
             * `ref_tester` 테이블에 `tester` 테이블의 기본 키를 참조하는 레코드를 추가한다.
             *
             * ```sql
             * INSERT INTO ref_tester (reference) VALUES (0194b78c-d028-73a2-933b-2fe80bf31095)
             * ```
             */
            referenceTester.insert {
                it[reference] = transformedId
            }

            val referenceId = referenceTester.selectAll().single()[referenceTester.reference]
            referenceId.value shouldBeEqualTo CustomId(uuid)
        }
    }

    /**
     * Application에서 null 을 지정하면, DB에는 특정 값(-1)으로 저장되도록 하는 예제
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `null to non-null transform`(testDB: TestDB) = runTest {
        /**
         * ```sql
         * CREATE TABLE IF NOT EXISTS TESTER (
         *      ID INT AUTO_INCREMENT PRIMARY KEY,
         *      "value" INT NULL
         * )
         * ```
         */
        val tester = object: IntIdTable("tester") {
            val value: Column<Int?> = integer("value")
                .nullable()
                .transform(wrap = { if (it == -1) null else it }, unwrap = { it ?: -1 })   // null 이 지정되면 -1로 DB에 저장
        }
        val rawTester = object: IntIdTable("tester") {
            val value: Column<Int?> = integer("value").nullable()
        }

        withTables(testDB, tester) {
            tester.insert {
                it[value] = null
            }

            tester.selectAll().single()[tester.value] shouldBeEqualTo null
            rawTester.selectAll().single()[rawTester.value] shouldBeEqualTo -1
        }
    }

    /**
     * Application에서 null 을 지정하면, DB에는 특정 값(-1)으로 저장되도록 하는 예제
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `null to non-null recursive transform`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("tester") {
            val value: Column<Holder?> = integer("value")
                .nullable()
                .transform(wrap = { if (it == -1) null else it }, unwrap = { it ?: -1 })
                .transform(HolderNullableTransformer())
        }
        val rawTester = object: IntIdTable("tester") {
            val value: Column<Long?> = long("value").nullable()
        }

        withTables(testDB, tester) {
            val id1 = tester.insertAndGetId {
                it[value] = Holder(100)
            }
            tester.selectAll().where { tester.id eq id1 }.single()[tester.value]?.value shouldBeEqualTo 100
            rawTester.selectAll().where { rawTester.id eq id1 }.single()[rawTester.value] shouldBeEqualTo 100L

            val id2 = tester.insertAndGetId {
                it[value] = null
            }

            tester.selectAll().where { tester.id eq id2 }.single()[tester.value]?.value.shouldBeNull()
            rawTester.selectAll().where { rawTester.id eq id2 }.single()[rawTester.value] shouldBeEqualTo -1L
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `null transform`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("tester") {
            val value: Column<Holder?> = integer("value")
                .nullTransform(HolderNullTransformer())
        }
        val rawTester = object: IntIdTable("tester") {
            val value: Column<Int> = integer("value")
        }

        withTables(testDB, tester) {
            val result = tester.insert {
                it[value] = null
            }
            result[tester.value].shouldBeNull()
            tester.selectAll().single()[tester.value].shouldBeNull()
            rawTester.selectAll().single()[rawTester.value] shouldBeEqualTo 0
        }
    }

    /**
     * [ColumnTransformer]를 상속받아 구현한 [HolderTransformer] 를 사용하는 예제
     *
     * 여기에 기본 값을 value class로 지정할 수 있다.
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS tester (
     *      id SERIAL PRIMARY KEY,
     *      "value" INT DEFAULT 1 NOT NULL
     * );
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transform with default`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("tester") {
            val value: Column<Holder> = integer("value")
                .transform(HolderTransformer())
                .default(Holder(1))
        }

        withTables(testDB, tester) {
            // 기본 값이 지정되어 있으므로, 값을 지정하지 않아도 된다.
            val entry = tester.insert { }
            entry[tester.value] shouldBeEqualTo Holder(1)

            // INSERT INTO tester  DEFAULT VALUES
            tester.selectAll().first()[tester.value] shouldBeEqualTo Holder(1)
        }
    }

    /**
     * Batch Insert 시에도 transform 이 적용되는지 확인
     *
     * ```sql
     * INSERT INTO "TEST-BATCH-INSERT" (V1) VALUES (1)
     * INSERT INTO "TEST-BATCH-INSERT" (V1) VALUES (2)
     * INSERT INTO "TEST-BATCH-INSERT" (V1) VALUES (3)
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transform in batch insert`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("test-batch-insert") {
            val v1 = integer("v1")
                .transform(wrap = { Holder(it) }, unwrap = { it.value })
        }

        withTables(testDB, tester) {
            tester.batchInsert(listOf(1, 2, 3)) {
                this[tester.v1] = Holder(it)
            }

            tester.selectAll()
                .orderBy(tester.v1)
                .map { it[tester.v1].value }
                .toList() shouldBeEqualTo listOf(1, 2, 3)
        }
    }

    /**
     * INSERT 시 뿐 아니라 UPDATE 시에도 transform 이 적용되는지 확인
     *
     * ```sql
     * INSERT INTO "TEST-UPDATE" (V1) VALUES (1)
     * ```
     *
     * UPDATE
     * ```sql
     * UPDATE "TEST-UPDATE" SET V1=2 WHERE "TEST-UPDATE".ID = 1
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `transform in update`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("test-update") {
            val v1 = integer("v1")
                .transform(wrap = { Holder(it) }, unwrap = { it.value })
        }

        withTables(testDB, tester) {
            val id = tester.insertAndGetId {
                it[v1] = Holder(1)
            }

            tester.update(where = { tester.id eq id }) {
                it[tester.v1] = Holder(2)
            }

            tester.selectAll().first()[tester.v1].value shouldBeEqualTo 2
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `wrapRow with aliases`(testDB: TestDB) = runTest {
        withTables(testDB, TransformTable) {
            TransformTable.insert {
                it[simple] = Holder(10)
            }

            val e2 = TransformTable.selectAll().map { it[simple] }.single()
            e2 shouldBeEqualTo Holder(10)

            val e3 = TransformTable.selectAll().map { it[simple] }.single()
            e3 shouldBeEqualTo Holder(10)
        }
    }
}
