package exposed.r2dbc.shared.mapping.compositeId

import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * Composite Id 를 가지는 Entity를 표현하는 ERD 입니다.
 */
object BookSchema {
    val allTables = arrayOf(Publishers, Authors, Books, Reviews, Offices)

    /**
     * 두 개의 DB 생성 key column(int, uuid)을 가진 CompositeIdTable 예시
     *
     * ```sql
     * -- PostgreSQL
     * CREATE TABLE IF NOT EXISTS publishers (
     *      pub_id SERIAL,
     *      isbn_code uuid,
     *      publisher_name VARCHAR(32) NOT NULL,
     *
     *      CONSTRAINT pk_publishers PRIMARY KEY (pub_id, isbn_code)
     * )
     * ```
     */
    object Publishers: CompositeIdTable("publishers") {
        val pubId = integer("pub_id").autoIncrement().entityId()
        val isbn = javaUUID("isbn_code").autoGenerate().entityId()
        val name = varchar("publisher_name", 32)

        override val primaryKey = PrimaryKey(pubId, isbn)
    }

    /**
     * [Publishers] 테이블을 참조하는 Author 테이블
     *
     * ```sql
     * -- PostgreSQL
     * CREATE TABLE IF NOT EXISTS authors (
     *      id SERIAL PRIMARY KEY,
     *      publisher_id INT NOT NULL,
     *      publisher_isbn uuid NOT NULL,
     *      pen_name VARCHAR(32) NOT NULL,
     *
     *      CONSTRAINT fk_authors_publisher_id_publisher_isbn__pub_id_isbn_code FOREIGN KEY (publisher_id, publisher_isbn)
     *      REFERENCES publishers(pub_id, isbn_code) ON DELETE RESTRICT ON UPDATE RESTRICT
     * )
     * ```
     */
    object Authors: IntIdTable("authors") {
        val publisherId = integer("publisher_id")
        val publisherIsbn = javaUUID("publisher_isbn")
        val penName = varchar("pen_name", 32)

        // 여러 column으로 구성된 FK 제약은 table-level constraint로 생성된다.
        init {
            foreignKey(publisherId, publisherIsbn, target = Publishers.primaryKey)
        }
    }

    /**
     * 하나의 DB 생성 int key column을 가진 CompositeIdTable 예시
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS books (
     *      book_id SERIAL PRIMARY KEY,
     *      title VARCHAR(32) NOT NULL,
     *      author_id INT NULL,
     *
     *      CONSTRAINT fk_books_author_id__id FOREIGN KEY (author_id)
     *      REFERENCES authors(id) ON DELETE RESTRICT ON UPDATE RESTRICT
     * );
     * ```
     */
    object Books: CompositeIdTable("books") {
        val bookId = integer("book_id").autoIncrement().entityId()
        val title = varchar("title", 32)
        val author = optReference("author_id", Authors)

        override val primaryKey = PrimaryKey(bookId)
    }

    /**
     * DB 생성이 아닌 string과 long 두 key column을 가진 CompositeIdTable 예시
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS reviews (
     *      code VARCHAR(8),
     *      "rank" BIGINT,
     *      book_id INT NOT NULL,
     *
     *      CONSTRAINT pk_reviews PRIMARY KEY (code, "rank"),
     *
     *      CONSTRAINT fk_reviews_book_id__book_id FOREIGN KEY (book_id)
     *      REFERENCES books(book_id) ON DELETE RESTRICT ON UPDATE RESTRICT
     * );
     * ```
     */
    object Reviews: CompositeIdTable("reviews") {
        val content = varchar("code", 8).entityId()
        val rank = long("rank").entityId()
        val book = integer("book_id")

        override val primaryKey = PrimaryKey(content, rank)

        init {
            foreignKey(book, target = Books.primaryKey)
        }
    }

    /**
     * DB 생성이 아닌 string, string, int 세 key column을 가진 CompositeIdTable 예시
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS offices (
     *      zip_code VARCHAR(8),
     *      "name" VARCHAR(64),
     *      area_code INT,
     *      staff BIGINT NULL,
     *      publisher_id INT NULL,
     *      publisher_isbn uuid NULL,
     *
     *      CONSTRAINT pk_offices PRIMARY KEY (zip_code, "name", area_code),
     *
     *      CONSTRAINT fk_offices_publisher_id_publisher_isbn__pub_id_isbn_code FOREIGN KEY (publisher_id, publisher_isbn)
     *      REFERENCES publishers(pub_id, isbn_code) ON DELETE RESTRICT ON UPDATE RESTRICT
     * );
     * ```
     */
    object Offices: CompositeIdTable("offices") {
        val zipCode = varchar("zip_code", 8).entityId()
        val name = varchar("name", 64).entityId()
        val areaCode = integer("area_code").entityId()
        val staff = long("staff").nullable()
        val publisherId = integer("publisher_id").nullable()
        val publisherIsbn = javaUUID("publisher_isbn").nullable()

        override val primaryKey = PrimaryKey(zipCode, name, areaCode)

        init {
            // Publishers 는 publisherId, publisherIsbn 두 컬럼으로 구성된 PK
            foreignKey(publisherId, publisherIsbn, target = Publishers.primaryKey)
        }
    }
}
