package exposed.r2dbc.multitenant.ktor.tenant

import exposed.r2dbc.multitenant.ktor.domain.model.ActorRecord
import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.ktor.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.multitenant.ktor.domain.model.MovieWithActorRecord
import exposed.r2dbc.multitenant.ktor.tenant.Tenants.Tenant
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
import java.time.LocalDate

/**
 * Creates tenant schemas and inserts deterministic sample data.
 */
class DataInitializer(
    private val database: R2dbcDatabase,
) {

    companion object: KLoggingChannel()

    suspend fun initializeAll() {
        Tenant.entries.forEach { tenant ->
            initialize(tenant)
        }
    }

    suspend fun initialize(tenant: Tenant) {
        createSchema(tenant)
        populateData(tenant)
    }

    private suspend fun createSchema(tenant: Tenant) {
        suspendTransaction(db = database) {
            val schema = getSchemaDefinition(tenant)
            SchemaUtils.createSchema(schema)
            SchemaUtils.setSchema(schema)
            SchemaUtils.create(ActorTable, MovieTable, ActorInMovieTable)
        }
    }

    private suspend fun populateData(tenant: Tenant) {
        suspendTransactionWithTenant(tenant = tenant, db = database) {
            if (ActorTable.selectAll().count() > 0) {
                log.info { "Sample data already exists for tenant=${tenant.id}" }
                return@suspendTransactionWithTenant
            }

            val johnnyDepp = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Johnny", "Depp", "1973-06-09")
                Tenant.KOREAN -> ActorRecord(0L, "조니", "뎁", "1979-10-28")
            }
            val bradPitt = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Brad", "Pitt", "1970-12-18")
                Tenant.KOREAN -> ActorRecord(0L, "브래드", "피트", "1982-05-16")
            }
            val angelinaJolie = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Angelina", "Jolie", "1983-11-10")
                Tenant.KOREAN -> ActorRecord(0L, "안제리나", "졸리", "1983-11-10")
            }
            val jenniferAniston = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Jennifer", "Aniston", "1975-07-23")
                Tenant.KOREAN -> ActorRecord(0L, "제니퍼", "애니스톤", "1975-07-23")
            }
            val angelinaGrace = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Angelina", "Grace", "1988-09-02")
                Tenant.KOREAN -> ActorRecord(0L, "안젤리나", "그레이스", "1988-09-02")
            }
            val craigDaniel = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Craig", "Daniel", "1970-11-12")
                Tenant.KOREAN -> ActorRecord(0L, "다니엘", "크레이그", "1970-11-12")
            }
            val ellenPaige = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Ellen", "Paige", "1981-12-20")
                Tenant.KOREAN -> ActorRecord(0L, "엘렌", "페이지", "1981-12-20")
            }
            val russellCrowe = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Russell", "Crowe", "1970-01-20")
                Tenant.KOREAN -> ActorRecord(0L, "러셀", "크로우", "1970-01-20")
            }
            val edwardNorton = when (tenant) {
                Tenant.ENGLISH -> ActorRecord(0L, "Edward", "Norton", "1975-04-03")
                Tenant.KOREAN -> ActorRecord(0L, "에드워드", "노튼", "1975-04-03")
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
                    name = if (tenant == Tenant.KOREAN) "글래디에이터" else "Gladiator",
                    producerName = johnnyDepp.firstName,
                    releaseDate = "2000-05-01",
                    actors = listOf(russellCrowe, ellenPaige, craigDaniel),
                ),
                MovieWithActorRecord(
                    name = if (tenant == Tenant.KOREAN) "가디언스 오브 갤럭시" else "Guardians of the galaxy",
                    producerName = johnnyDepp.firstName,
                    releaseDate = "2014-07-21",
                    actors = listOf(angelinaGrace, bradPitt, ellenPaige, angelinaJolie, johnnyDepp),
                ),
                MovieWithActorRecord(
                    name = if (tenant == Tenant.KOREAN) "싸움 클럽" else "Fight club",
                    producerName = craigDaniel.firstName,
                    releaseDate = "1999-09-13",
                    actors = listOf(bradPitt, jenniferAniston, edwardNorton),
                ),
                MovieWithActorRecord(
                    name = if (tenant == Tenant.KOREAN) "13가지 이유" else "13 Reasons Why",
                    producerName = "Suzuki",
                    releaseDate = "2016-01-01",
                    actors = listOf(angelinaJolie, jenniferAniston),
                ),
            )

            ActorTable.batchInsert(actors) { actor ->
                this[ActorTable.firstName] = actor.firstName
                this[ActorTable.lastName] = actor.lastName
                actor.birthday?.let { birthday ->
                    this[ActorTable.birthday] = LocalDate.parse(birthday)
                }
            }
            MovieTable.batchInsert(movies) { movie ->
                this[MovieTable.name] = movie.name
                this[MovieTable.producerName] = movie.producerName
                this[MovieTable.releaseDate] = LocalDate.parse(movie.releaseDate)
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
}
