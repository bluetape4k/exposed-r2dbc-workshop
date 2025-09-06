package exposed.r2dbc.workshop.springwebflux.domain

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

object MovieSchema {

    object MovieTable: LongIdTable("movies") {
        val name = varchar("name", 255).index()
        val producerName = varchar("producer_name", 255).index()
        val releaseDate = datetime("release_date")
    }

    /**
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

    object ActorInMovieTable: Table("actors_in_movies") {
        val movieId = reference("movie_id", MovieTable, onDelete = ReferenceOption.CASCADE)
        val actorId = reference("actor_id", ActorTable, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(movieId, actorId)
    }

}
