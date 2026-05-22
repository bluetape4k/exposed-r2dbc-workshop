package exposed.r2dbc.multitenant.connectionfactory.tenant

import exposed.r2dbc.multitenant.connectionfactory.domain.model.ActorRecord
import exposed.r2dbc.multitenant.connectionfactory.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.multitenant.connectionfactory.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.connectionfactory.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.multitenant.connectionfactory.domain.model.MovieWithActorRecord
import exposed.r2dbc.multitenant.connectionfactory.tenant.Tenants.Tenant
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Creates tenant databases and loads tenant-specific sample data.
 */
@Component
class DataInitializer(
    @param:Qualifier("tenantInitializerDatabases")
    private val tenantDatabases: Map<Tenant, R2dbcDatabase>,
) {

    companion object: KLoggingChannel()

    /**
     * Initializes the database owned by [tenant].
     */
    suspend fun initialize(tenant: Tenant) {
        log.info { "Initializing tenant database. tenant=${tenant.id}" }

        val database = tenantDatabases[tenant] ?: error("No initializer database for tenant '${tenant.id}'")
        suspendTransaction(db = database) {
            SchemaUtils.create(ActorTable, MovieTable, ActorInMovieTable)

            val totalActors = ActorTable.selectAll().count()
            if (totalActors > 0) {
                log.info { "Tenant database already has sample data. tenant=${tenant.id}" }
                return@suspendTransaction
            }

            populateData(tenant)
        }
    }

    private suspend fun populateData(tenant: Tenant) {
        log.info { "Inserting sample actors and movies. tenant=${tenant.id}" }

        val johnnyDepp = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Johnny", "Depp", "1973-06-09")
            Tenant.KOREAN  -> ActorRecord(0L, "조니", "뎁", "1979-10-28")
        }
        val bradPitt = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Brad", "Pitt", "1970-12-18")
            Tenant.KOREAN  -> ActorRecord(0L, "브래드", "피트", "1982-05-16")
        }
        val angelinaJolie = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Angelina", "Jolie", "1983-11-10")
            Tenant.KOREAN  -> ActorRecord(0L, "안제리나", "졸리", "1983-11-10")
        }
        val jenniferAniston = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Jennifer", "Aniston", "1975-07-23")
            Tenant.KOREAN  -> ActorRecord(0L, "제니퍼", "애니스톤", "1975-07-23")
        }
        val angelinaGrace = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Angelina", "Grace", "1988-09-02")
            Tenant.KOREAN  -> ActorRecord(0L, "안젤리나", "그레이스", "1988-09-02")
        }
        val craigDaniel = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Craig", "Daniel", "1970-11-12")
            Tenant.KOREAN  -> ActorRecord(0L, "다니엘", "크레이그", "1970-11-12")
        }
        val ellenPaige = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Ellen", "Paige", "1981-12-20")
            Tenant.KOREAN  -> ActorRecord(0L, "엘렌", "페이지", "1981-12-20")
        }
        val russellCrowe = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Russell", "Crowe", "1970-01-20")
            Tenant.KOREAN  -> ActorRecord(0L, "러셀", "크로우", "1970-01-20")
        }
        val edwardNorton = when (tenant) {
            Tenant.ENGLISH -> ActorRecord(0L, "Edward", "Norton", "1975-04-03")
            Tenant.KOREAN  -> ActorRecord(0L, "에드워드", "노튼", "1975-04-03")
        }

        val actors = listOf(
            johnnyDepp,
            bradPitt,
            angelinaJolie,
            jenniferAniston,
            angelinaGrace,
            craigDaniel,
            ellenPaige,
            russellCrowe,
            edwardNorton,
        )

        val movies = listOf(
            MovieWithActorRecord(
                0L,
                if (tenant == Tenant.KOREAN) "글래디에이터" else "Gladiator",
                johnnyDepp.firstName,
                "2000-05-01",
                mutableListOf(russellCrowe, ellenPaige, craigDaniel),
            ),
            MovieWithActorRecord(
                0L,
                if (tenant == Tenant.KOREAN) "가디언스 오브 갤럭시" else "Guardians of the galaxy",
                johnnyDepp.firstName,
                "2014-07-21",
                mutableListOf(angelinaGrace, bradPitt, ellenPaige, angelinaJolie, johnnyDepp),
            ),
            MovieWithActorRecord(
                0L,
                if (tenant == Tenant.KOREAN) "싸움 클럽" else "Fight club",
                craigDaniel.firstName,
                "1999-09-13",
                mutableListOf(bradPitt, jenniferAniston, edwardNorton),
            ),
            MovieWithActorRecord(
                0L,
                if (tenant == Tenant.KOREAN) "13가지 이유" else "13 Reasons Why",
                "Suzuki",
                "2016-01-01",
                mutableListOf(angelinaJolie, jenniferAniston),
            ),
        )

        ActorTable.batchInsert(actors) {
            this[ActorTable.firstName] = it.firstName
            this[ActorTable.lastName] = it.lastName
            it.birthday?.let { birthday ->
                this[ActorTable.birthday] = LocalDate.parse(birthday)
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

            val actorIds = movie.actors.map { actor ->
                ActorTable
                    .select(ActorTable.id)
                    .where { ActorTable.firstName eq actor.firstName }
                    .andWhere { ActorTable.lastName eq actor.lastName }
                    .first()[ActorTable.id]
            }

            ActorInMovieTable.batchInsert(actorIds.map { movieId to it }) {
                this[ActorInMovieTable.movieId] = it.first.value
                this[ActorInMovieTable.actorId] = it.second.value
            }
        }
    }
}
