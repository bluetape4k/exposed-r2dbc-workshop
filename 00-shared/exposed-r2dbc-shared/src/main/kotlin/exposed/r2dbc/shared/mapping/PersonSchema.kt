package exposed.r2dbc.shared.mapping

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import java.io.Serializable
import java.time.LocalDate

/**
 * Person/Address 도메인 스키마 정의 모듈.
 *
 * Exposed R2DBC에서 관계형 테이블(1:N, FK) 정의와 데이터 조작 패턴을 학습하기 위한
 * 공유 스키마입니다. [AddressTable]과 [PersonTable]로 구성되며,
 * 테스트 픽스처 함수([withPersons], [withPersonsAndAddress])를 통해
 * 미리 정의된 샘플 데이터를 쉽게 삽입할 수 있습니다.
 *
 * 주요 구성:
 * - [AddressTable]: 주소 테이블 (LongIdTable, 복합 인덱스 포함)
 * - [PersonTable]: 사람 테이블 (AddressTable 외래키 참조)
 * - [PersonTableDML]: INSERT-SELECT 등 DML 전용 테이블 뷰
 * - [PersonRecord], [PersonWithAddress], [AddressRecord]: 결과 매핑용 데이터 클래스
 */
object PersonSchema {

    /** [AddressTable]과 [PersonTable]을 포함하는 모든 Person 도메인 테이블 배열. */
    val allPersonTables = arrayOf(AddressTable, PersonTable)

    /**
     * 주소 정보를 저장하는 테이블.
     *
     * `city`, `zip`, `id` 컬럼으로 구성된 복합 인덱스(`idx_address_city_zip`)가 정의됩니다.
     *
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS addresses (
     *      id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *      street VARCHAR(255) NOT NULL,
     *      city VARCHAR(255) NOT NULL,
     *      `state` VARCHAR(32) NOT NULL,
     *      zip VARCHAR(10) NULL
     * );
     *
     * CREATE INDEX idx_address_city_zip ON addresses (city, zip, id)
     *
     * ```
     */
    object AddressTable: LongIdTable("addresses") {
        val street = varchar("street", 255)
        val city = varchar("city", 255)
        val state = varchar("state", 32)
        val zip = varchar("zip", 10).nullable()

        init {
            index("idx_address_city_zip", false, city, zip, id)
        }
    }

    /**
     * 사람 정보를 저장하는 테이블.
     *
     * [AddressTable]을 외래키로 참조하며, `id`와 `address_id` 쌍에 유니크 제약이 적용됩니다.
     * DB 컬럼명 `"employeed"`는 기존 스키마 호환성을 위해 유지하며,
     * Kotlin 프로퍼티명은 올바른 철자인 `employed`를 사용합니다.
     *
     * ```sql
     * -- MySQL V8
     * CREATE TABLE IF NOT EXISTS persons (
     *      id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *      first_name VARCHAR(50) NOT NULL,
     *      last_name VARCHAR(50) NOT NULL,
     *      birth_date DATE NOT NULL,
     *      employeed BOOLEAN DEFAULT TRUE NOT NULL,
     *      occupation VARCHAR(255) NULL,
     *      address_id BIGINT NOT NULL,
     *
     *      CONSTRAINT fk_persons_address_id__id FOREIGN KEY (address_id)
     *      REFERENCES addresses(id) ON DELETE RESTRICT ON UPDATE RESTRICT
     * );
     *
     * ALTER TABLE persons ADD CONSTRAINT idx_person_addr UNIQUE (id, address_id)
     * ```
     */
    object PersonTable: LongIdTable("persons") {
        val firstName = varchar("first_name", 50)
        val lastName = varchar("last_name", 50)
        val birthDate = date("birth_date")

        /**
         * 고용 여부 컬럼. DB 컬럼명은 기존 스키마 호환성을 위해 `"employeed"` 유지.
         * Kotlin 프로퍼티명은 올바른 철자인 `employed`를 사용합니다.
         */
        val employed = bool("employeed").default(true)
        val occupation = varchar("occupation", 255).nullable()
        val addressId = reference("address_id", AddressTable)  // many to one

        init {
            uniqueIndex("idx_person_addr", id, addressId)
        }
    }

    /**
     * INSERT-SELECT 등 순수 DML 작업을 위한 `persons` 테이블 뷰.
     *
     * [PersonTable]과 동일한 DB 테이블(`persons`)을 참조하지만,
     * `id` 컬럼에 `autoIncrement()`를 지정하지 않아 INSERT-SELECT 시
     * ID 값을 직접 지정할 수 있습니다.
     *
     * DB 컬럼명 `"employeed"`는 기존 스키마 호환성을 위해 유지하며,
     * Kotlin 프로퍼티명은 올바른 철자인 `employed`를 사용합니다.
     */
    object PersonTableDML: Table("persons") {
        val id = long("id")   // autoIncrement() 를 지정하면 insert select 같은 id 에 값을 지정하는 것이 불가능하다.
        val firstName = varchar("first_name", 50)
        val lastName = varchar("last_name", 50)
        val birthDate = date("birth_date")

        /**
         * 고용 여부 컬럼. DB 컬럼명은 기존 스키마 호환성을 위해 `"employeed"` 유지.
         * Kotlin 프로퍼티명은 올바른 철자인 `employed`를 사용합니다.
         */
        val employed = bool("employeed").default(true)
        val occupation = varchar("occupation", 255).nullable()
        val addressId = long("address_id")  // many to one

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * `persons` 테이블 조회 결과를 담는 데이터 클래스.
     *
     * @property id 사람 고유 식별자
     * @property firstName 이름
     * @property lastName 성
     * @property birthDate 생년월일
     * @property employed 고용 여부 (DB 컬럼명: `employeed`)
     * @property occupation 직업
     * @property address 주소 ID (외래키)
     */
    data class PersonRecord(
        val id: Long? = null,
        val firstName: String? = null,
        val lastName: String? = null,
        val birthDate: LocalDate? = null,
        val employed: Boolean? = null,
        val occupation: String? = null,
        val address: Long? = null,
    ): Serializable

    /**
     * 주소 정보를 포함한 사람 정보 데이터 클래스.
     *
     * JOIN 쿼리 결과를 매핑할 때 사용합니다.
     *
     * @property id 사람 고유 식별자
     * @property firstName 이름
     * @property lastName 성
     * @property birthDate 생년월일
     * @property employed 고용 여부 (DB 컬럼명: `employeed`)
     * @property occupation 직업
     * @property address 주소 정보 ([AddressRecord])
     */
    data class PersonWithAddress(
        var id: Long? = null,
        var firstName: String? = null,
        var lastName: String? = null,
        var birthDate: LocalDate? = null,
        var employed: Boolean? = null,
        var occupation: String? = null,
        var address: AddressRecord? = null,
    ): Serializable

    /**
     * `addresses` 테이블 조회 결과를 담는 데이터 클래스.
     *
     * @property id 주소 고유 식별자
     * @property street 도로명 주소
     * @property city 도시
     * @property state 주(State) 또는 시도
     * @property zip 우편번호
     */
    data class AddressRecord(
        val id: Long? = null,
        val street: String? = null,
        val city: String? = null,
        val state: String? = null,
        val zip: String? = null,
    ): Serializable

    /**
     * 테스트용 Person/Address 테이블을 생성하고 빈 상태로 블록을 실행합니다.
     *
     * 테이블 생성 → [block] 실행 → 테이블 정리 순서로 동작합니다.
     *
     * @param testDB 테스트에 사용할 데이터베이스
     * @param block 테이블을 인자로 받아 실행할 suspend 블록
     */
    @Suppress("UnusedReceiverParameter")
    suspend fun AbstractR2dbcExposedTest.withPersons(
        testDB: TestDB,
        block: suspend R2dbcTransaction.(PersonTable, AddressTable) -> Unit,
    ) {
        withTables(testDB, *allPersonTables) {
            block(PersonTable, AddressTable)
        }
    }

    /**
     * 테스트용 Person/Address 테이블을 생성하고 샘플 데이터를 삽입한 후 블록을 실행합니다.
     *
     * 주소 3개(Bedrock 2곳, Seoul 1곳)와 사람 8명(Flintstone/Rubble 가족, Bae 가족)
     * 데이터를 자동으로 삽입합니다.
     *
     * @param testDB 테스트에 사용할 데이터베이스
     * @param statement Person/Address 테이블을 인자로 받아 실행할 suspend 블록
     */
    @Suppress("UnusedReceiverParameter")
    suspend fun AbstractR2dbcExposedTest.withPersonsAndAddress(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            persons: PersonSchema.PersonTable,
            addresses: PersonSchema.AddressTable,
        ) -> Unit,
    ) {
        val persons = PersonSchema.PersonTable
        val addresses = PersonSchema.AddressTable

        withTables(testDB, *PersonSchema.allPersonTables) {

            val addr1 = AddressTable.insertAndGetId {
                it[street] = "123 Main St"
                it[city] = "Bedrock"
                it[state] = "IN"
                it[zip] = "12345"
            }
            val addr2 = AddressTable.insertAndGetId {
                it[street] = "456 Elm St"
                it[city] = "Bedrock"
                it[state] = "IN"
                it[zip] = "12345"
            }

            val addr3 = AddressTable.insertAndGetId {
                it[street] = "165 Kangnam-daero"
                it[city] = "Seoul"
                it[state] = "Seoul"
                it[zip] = "11111"
            }

            PersonTable.insert {
                it[firstName] = "Fred"
                it[lastName] = "Flintstone"
                it[birthDate] = LocalDate.of(1935, 2, 1)
                it[employed] = true
                it[occupation] = "Brontosaurus Operator"
                it[addressId] = addr1
            }
            PersonTable.insert {
                it[firstName] = "Wilma"
                it[lastName] = "Flintstone"
                it[birthDate] = LocalDate.of(1940, 2, 1)
                it[employed] = false
                it[occupation] = "Accountant"
                it[addressId] = addr1
            }
            PersonTable.insert {
                it[firstName] = "Pebbles"
                it[lastName] = "Flintstone"
                it[birthDate] = LocalDate.of(1960, 5, 6)
                it[employed] = false
                it[addressId] = addr1
            }

            PersonTable.insert {
                it[firstName] = "Barney"
                it[lastName] = "Rubble"
                it[birthDate] = LocalDate.of(1937, 2, 1)
                it[employed] = true
                it[occupation] = "Brontosaurus Operator"
                it[addressId] = addr2
            }
            PersonTable.insert {
                it[firstName] = "Betty"
                it[lastName] = "Rubble"
                it[birthDate] = LocalDate.of(1943, 2, 1)
                it[employed] = false
                it[occupation] = "Engineer"
                it[addressId] = addr2
            }
            PersonTable.insert {
                it[firstName] = "Bamm Bamm"
                it[lastName] = "Rubble"
                it[birthDate] = LocalDate.of(1963, 7, 8)
                it[employed] = false
                it[addressId] = addr2
            }

            PersonTable.insert {
                it[firstName] = "Sunghyouk"
                it[lastName] = "Bae"
                it[birthDate] = LocalDate.of(1968, 10, 14)
                it[employed] = false
                it[addressId] = addr3
            }

            PersonTable.insert {
                it[firstName] = "Jehyoung"
                it[lastName] = "Bae"
                it[birthDate] = LocalDate.of(1996, 5, 22)
                it[employed] = false
                it[addressId] = addr3
            }

            statement(persons, addresses)
        }
    }

}
