package exposed.r2dbc.examples.utils

import exposed.r2dbc.examples.domain.model.ActorRecord
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.examples.domain.model.MovieWithActorRecord
import io.bluetape4k.collections.eclipse.fastListOf
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

        val johnnyDepp = ActorRecord(0, "Johnny", "Depp", "1979-10-28")
        val bradPitt = ActorRecord(0, "Brad", "Pitt", "1982-05-16")
        val angelinaJolie = ActorRecord(0, "Angelina", "Jolie", "1983-11-10")
        val jenniferAniston = ActorRecord(0, "Jennifer", "Aniston", "1975-07-23")
        val angelinaGrace = ActorRecord(0, "Angelina", "Grace", "1988-09-02")
        val craigDaniel = ActorRecord(0, "Craig", "Daniel", "1970-11-12")
        val ellenPaige = ActorRecord(0, "Ellen", "Paige", "1981-12-20")
        val russellCrowe = ActorRecord(0, "Russell", "Crowe", "1970-01-20")
        val edwardNorton = ActorRecord(0, "Edward", "Norton", "1975-04-03")

        val actors = fastListOf(
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

        val movies = fastListOf(
            MovieWithActorRecord(
                0,
                "Gladiator",
                johnnyDepp.firstName,
                "2000-05-01",
                fastListOf(russellCrowe, ellenPaige, craigDaniel)
            ),
            MovieWithActorRecord(
                0,
                "Guardians of the galaxy",
                johnnyDepp.firstName,
                "2014-07-21",
                fastListOf(angelinaGrace, bradPitt, ellenPaige, angelinaJolie, johnnyDepp)
            ),
            MovieWithActorRecord(
                0,
                "Fight club",
                craigDaniel.firstName,
                "1999-09-13",
                fastListOf(bradPitt, jenniferAniston, edwardNorton)
            ),
            MovieWithActorRecord(
                0,
                "13 Reasons Why",
                "Suzuki",
                "2016-01-01",
                fastListOf(angelinaJolie, jenniferAniston)
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
                .limit(1)
                .first()[MovieTable.id]

            movie.actors.forEach { actor ->
                val actorId = ActorTable
                    .select(ActorTable.id)
                    .where { ActorTable.firstName eq actor.firstName }
                    .andWhere { ActorTable.lastName eq actor.lastName }
                    .limit(1)
                    .first()[ActorTable.id]

                ActorInMovieTable.insert {
                    it[ActorInMovieTable.actorId] = actorId.value
                    it[ActorInMovieTable.movieId] = movieId.value
                }
            }
        }
    }
}
