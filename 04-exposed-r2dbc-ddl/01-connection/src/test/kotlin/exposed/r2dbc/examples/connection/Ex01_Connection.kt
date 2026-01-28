package exposed.r2dbc.examples.connection

import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.sql.Types

class Ex01_Connection: R2dbcExposedTestBase() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- H2
     * CREATE TABLE IF NOT EXISTS PEOPLE (
     *      ID BIGINT AUTO_INCREMENT PRIMARY KEY,
     *      FIRSTNAME VARCHAR(80) NULL,
     *      LASTNAME VARCHAR(42) DEFAULT 'Doe' NOT NULL,
     *      AGE INT DEFAULT 18 NOT NULL
     * );
     * ```
     */
    object People: LongIdTable() {
        val firstName: Column<String?> = varchar("firstname", 80).nullable()
        val lastName: Column<String> = varchar("lastname", 42).default("Doe")
        val age: Column<Int> = integer("age").default(18)
    }

    /**
     * 테이블 컬럼의 메타데이터를 가져온다.
     */
    @Test
    fun `getting column metadata`() = runTest {
        withTables(TestDB.H2, People) {
            val columnMetadata = connection().metadata {
                columns(People)[People].shouldNotBeNull()
            }.toSet()

            val h2Dialect = (db.dialect as H2Dialect)
            val idType = "BIGINT"
            val firstNameType =
                if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(80)" else "VARCHAR(80)"
            val lastNameType =
                if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(42)" else "VARCHAR(42)"
            val ageType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "INTEGER" else "INT"

            val expected = setOf(
                ColumnMetadata(
                    People.id.nameInDatabaseCase(),
                    Types.BIGINT,
                    idType,
                    false,
                    64,
                    null,
                    h2Dialect.h2Mode != H2Dialect.H2CompatibilityMode.Oracle,
                    null
                ),
                ColumnMetadata(
                    People.firstName.nameInDatabaseCase(),
                    Types.VARCHAR,
                    firstNameType,
                    true,
                    80,
                    null,
                    false,
                    null
                ),
                ColumnMetadata(
                    People.lastName.nameInDatabaseCase(),
                    Types.VARCHAR,
                    lastNameType,
                    false,
                    42,
                    null,
                    false,
                    "Doe"
                ),
                ColumnMetadata(
                    People.age.nameInDatabaseCase(),
                    Types.INTEGER,
                    ageType,
                    false,
                    32,
                    null,
                    false,
                    "18"
                ),
            )

            columnMetadata shouldContainSame expected
        }
    }

    /**
     * 테이블 제약조건을 가져온다.
     *
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS parent (
     *      id BIGSERIAL PRIMARY KEY,
     *      "scale" INT NOT NULL
     * );
     *
     * ALTER TABLE parent ADD CONSTRAINT parent_scale_unique UNIQUE ("scale");
     *
     * CREATE TABLE IF NOT EXISTS child (
     *      id BIGSERIAL PRIMARY KEY,
     *      "scale" INT NOT NULL,
     *
     *      CONSTRAINT fk_child_scale__scale FOREIGN KEY ("scale") REFERENCES parent("scale")
     *      ON DELETE RESTRICT ON UPDATE RESTRICT
     * );
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `table constraints`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB != TestDB.MYSQL_V5 }

        val parent = object: LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object: LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }

        withTables(testDB, parent, child) {
            /**
             * child 테이블과 관련된 테이블의 제약조건 정보를 가져온다.
             *
             * ```
             * key: parent, constraint: []
             * key: child, constraint: [ForeignKeyConstraint(fkName='fk_child_scale__scale')]
             * ```
             */
            val constraints = connection().metadata {
                tableConstraints(listOf(child))
            }

            constraints.forEach { (key, constraints) ->
                log.debug { "key: $key, constraints: $constraints" }
            }
            constraints.keys shouldHaveSize 2   // parent, child
        }
    }
}
