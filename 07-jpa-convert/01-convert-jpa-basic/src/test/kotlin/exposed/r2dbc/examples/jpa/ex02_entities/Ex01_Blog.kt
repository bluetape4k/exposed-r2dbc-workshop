package exposed.r2dbc.examples.jpa.ex02_entities

import exposed.r2dbc.examples.jpa.ex02_entities.BlogSchema.PostDetailTable
import exposed.r2dbc.examples.jpa.ex02_entities.BlogSchema.PostTable
import exposed.r2dbc.examples.jpa.ex02_entities.BlogSchema.blogTables
import exposed.r2dbc.shared.tests.R2dbcExposedTestBase
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

class Ex01_Blog: R2dbcExposedTestBase() {

    companion object: KLogging()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `create blog entities`(testDB: TestDB) = runTest {
        withDb(testDB) {
            SchemaUtils.create(*blogTables)
            try {
                blogTables.all { it.exists() }.shouldBeTrue()
            } finally {
                SchemaUtils.drop(*blogTables)
            }
        }
    }

    /**
     * ```sql
     * -- Postgres
     * INSERT INTO posts (title) VALUES ('Post 1');
     *
     * INSERT INTO post_details (id, created_on, created_by)
     * VALUES (1, '2025-02-06', 'admin');
     * ```
     *
     * ```sql
     * -- Postgres
     * SELECT posts.id, posts.title
     *   FROM posts
     *  WHERE posts.id = 1;
     *
     * -- lazy loading for PostDetail of Post
     * SELECT post_details.id,
     *        post_details.created_on,
     *        post_details.created_by
     *   FROM post_details
     *  WHERE post_details.id = 1
     * ```
     */
    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `create post by DAO`(testDB: TestDB) = runTest {
        withTables(testDB, *blogTables) {

            val postId = PostTable.insertAndGetId {
                it[title] = "Post 1"
            }
            log.debug { "Post id=$postId" }

            // one-to-one 관계에서 ownership 을 가진 Post의 id 값을 지정합니다.
            val postDetailId = PostDetailTable.insertAndGetId {
                it[PostDetailTable.id] = postId
                it[createdOn] = LocalDate.now()
                it[createdBy] = "admin"
            }
            log.debug { "PostDetail Id=$postDetailId" }

            val row = PostTable.selectAll().where { PostTable.id eq postId }.single()

            row[PostTable.id] shouldBeEqualTo postId
            row[PostTable.id] shouldBeEqualTo postDetailId
        }
    }
}
