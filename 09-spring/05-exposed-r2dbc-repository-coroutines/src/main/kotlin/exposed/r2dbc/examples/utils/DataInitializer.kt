package exposed.r2dbc.examples.utils

import exposed.r2dbc.examples.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.examples.dto.ActorDTO
import exposed.r2dbc.examples.dto.MovieWithActorDTO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import java.time.LocalDate


@Component
class DataInitializer: ApplicationListener<ApplicationReadyEvent> {

    companion object: KLoggingChannel()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        runBlocking(Dispatchers.IO) {
            suspendTransaction {
                createTables()
                populateData()
            }
        }
    }

    private suspend fun createTables() {
        log.info { "Creating tables ..." }
        SchemaUtils.create(
            MovieTable,
            ActorTable,
            ActorInMovieTable
        )
        log.info { "Tables created!" }
    }

    private suspend fun populateData() {
        val totalActors = ActorTable.selectAll().count()

        if (totalActors > 0) {
            log.info { "There appears to be data already present, not inserting test data!" }
            return
        }

        log.info { "Inserting sample movies and actors ..." }

        val johnnyDepp = ActorDTO(0, "Johnny", "Depp", "1979-10-28")
        val bradPitt = ActorDTO(0, "Brad", "Pitt", "1982-05-16")
        val angelinaJolie = ActorDTO(0, "Angelina", "Jolie", "1983-11-10")
        val jenniferAniston = ActorDTO(0, "Jennifer", "Aniston", "1975-07-23")
        val angelinaGrace = ActorDTO(0, "Angelina", "Grace", "1988-09-02")
        val craigDaniel = ActorDTO(0, "Craig", "Daniel", "1970-11-12")
        val ellenPaige = ActorDTO(0, "Ellen", "Paige", "1981-12-20")
        val russellCrowe = ActorDTO(0, "Russell", "Crowe", "1970-01-20")
        val edwardNorton = ActorDTO(0, "Edward", "Norton", "1975-04-03")

        val actors = listOf(
            johnnyDepp,
            bradPitt,
            angelinaJolie,
            jenniferAniston,
            angelinaGrace,
            craigDaniel,
            ellenPaige,
            russellCrowe,
            edwardNorton
        )

        val movies = listOf(
            MovieWithActorDTO(
                0,
                "Gladiator",
                johnnyDepp.firstName,
                "2000-05-01",
                mutableListOf(russellCrowe, ellenPaige, craigDaniel)
            ),
            MovieWithActorDTO(
                0,
                "Guardians of the galaxy",
                johnnyDepp.firstName,
                "2014-07-21",
                mutableListOf(angelinaGrace, bradPitt, ellenPaige, angelinaJolie, johnnyDepp)
            ),
            MovieWithActorDTO(
                0,
                "Fight club",
                craigDaniel.firstName,
                "1999-09-13",
                mutableListOf(bradPitt, jenniferAniston, edwardNorton)
            ),
            MovieWithActorDTO(
                0,
                "13 Reasons Why",
                "Suzuki",
                "2016-01-01",
                mutableListOf(angelinaJolie, jenniferAniston)
            )
        )

        ActorTable.batchInsert(actors) {
            this[ActorTable.firstName] = it.firstName
            this[ActorTable.lastName] = it.lastName
            it.birthday?.let { birthDay ->
                this[ActorTable.birthday] = LocalDate.parse(birthDay)
            }
        }

        MovieTable.batchInsert(movies) {
            this[MovieTable.name] = it.name
            this[MovieTable.producerName] = it.producerName
            this[MovieTable.releaseDate] = LocalDate.parse(it.releaseDate)
        }

        movies.forEach { movie ->
            val movieId = MovieTable
                .select(MovieTable.id)
                .where { MovieTable.name eq movie.name }
                .first()[MovieTable.id]

            movie.actors.forEach { actor ->
                val actorId = ActorTable
                    .select(ActorTable.id)
                    .where { ActorTable.firstName eq actor.firstName }
                    .andWhere { ActorTable.lastName eq actor.lastName }
                    .first()[ActorTable.id]

                ActorInMovieTable.insert {
                    it[ActorInMovieTable.actorId] = actorId.value
                    it[ActorInMovieTable.movieId] = movieId.value
                }
            }
        }
    }
}
