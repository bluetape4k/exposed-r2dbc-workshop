package exposed.r2dbc.workshop.springwebflux.domain.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * Spring WebFlux + Exposed R2DBC 예제에서 사용하는 영화 도메인 스키마를 정의합니다.
 *
 * [MovieTable], [ActorTable], [ActorInMovieTable] 세 테이블로 구성되며
 * 영화-배우 다대다(M:N) 관계를 표현합니다.
 */
object MovieSchema {

    /**
     * 영화 정보를 저장하는 테이블입니다.
     *
     * - `name`: 영화 제목 (인덱스 적용)
     * - `producer_name`: 제작자 이름 (인덱스 적용)
     * - `release_date`: 개봉일시
     */
    object MovieTable: LongIdTable("movies") {
        val name = varchar("name", 255).index()
        val producerName = varchar("producer_name", 255).index()
        val releaseDate = datetime("release_date")
    }

    /**
     * 배우 정보를 저장하는 테이블입니다.
     *
     * ```sql
     * -- H2
     * CREATE TABLE IF NOT EXISTS actors (
     *     id BIGSERIAL PRIMARY KEY,
     *     first_name VARCHAR(255) NOT NULL,
     *     last_name VARCHAR(255) NOT NULL,
     *     birthday DATE NULL
     * )
     * ```
     */
    object ActorTable: LongIdTable("actors") {
        val firstName = varchar("first_name", 255).index()
        val lastName = varchar("last_name", 255).index()
        val birthday = date("birthday").nullable()
    }

    /**
     * 영화-배우 다대다(M:N) 관계를 저장하는 연결 테이블입니다.
     *
     * [MovieTable]과 [ActorTable]을 참조하며, 두 외래키를 복합 기본키로 사용합니다.
     * 영화 또는 배우 삭제 시 연관 레코드가 CASCADE 삭제됩니다.
     */
    object ActorInMovieTable: Table("actors_in_movies") {
        val movieId = reference("movie_id", MovieTable, onDelete = ReferenceOption.CASCADE)
        val actorId = reference("actor_id", ActorTable, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(movieId, actorId)
    }

}
