package exposed.r2dbc.multitenant.webflux.tenant

import exposed.r2dbc.multitenant.webflux.domain.dto.ActorDTO
import exposed.r2dbc.multitenant.webflux.domain.dto.MovieWithActorDTO
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.webflux.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.multitenant.webflux.tenant.Tenants.Tenant
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Component
import java.time.LocalDate


/**
 * Application 시작 시 DB 스키마 생성 및 샘플 데이터를 삽입하는 클래스
 */
@Component
class DataInitializer {

    companion object: KLoggingChannel()

    suspend fun initialize(tenant: Tenant) {
        log.info { "데이터베이스 초기화 및 샘플 데이터 추가" }

        createSchema(tenant)
        populateData(tenant)
    }

    private suspend fun createSchema(tenant: Tenant) {
        log.debug { "Creating schema and test data ..." }

        suspendTransaction {
            val currentSchema = getSchemaDefinition(tenant)
            SchemaUtils.createSchema(currentSchema)
            SchemaUtils.setSchema(currentSchema)


            SchemaUtils.create(ActorTable, MovieTable, ActorInMovieTable)
            // @Suppress("DEPRECATION")
            // SchemaUtils.createMissingTablesAndColumns()
        }
    }

    private suspend fun populateData(tenant: Tenant) {

        suspendTransactionWithTenant(tenant) {

            val totalActors = ActorTable.selectAll().count()

            if (totalActors > 0) {
                log.info { "There appears to be data already present, not inserting test data!" }
                return@suspendTransactionWithTenant
            }

            log.info { "Inserting sample actors and movies ..." }

            val johnnyDepp = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Johnny", "Depp", "1973-06-09")
                else -> ActorDTO(0L, "조니", "뎁", "1979-10-28")
            }
            val bradPitt = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Brad", "Pitt", "1970-12-18")
                else -> ActorDTO(0L, "브래드", "피트", "1982-05-16")
            }
            val angelinaJolie = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Angelina", "Jolie", "1983-11-10")
                else -> ActorDTO(0L, "안제리나", "졸리", "1983-11-10")
            }
            val jenniferAniston = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Jennifer", "Aniston", "1975-07-23")
                else -> ActorDTO(0L, "제니퍼", "애니스톤", "1975-07-23")
            }
            val angelinaGrace = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Angelina", "Grace", "1988-09-02")
                else -> ActorDTO(0L, "안젤리나", "그레이스", "1988-09-02")
            }
            val craigDaniel = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Craig", "Daniel", "1970-11-12")
                else -> ActorDTO(0L, "다니엘", "크레이그", "1970-11-12")
            }
            val ellenPaige = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Ellen", "Paige", "1981-12-20")
                else -> ActorDTO(0L, "엘렌", "페이지", "1981-12-20")
            }
            val russellCrowe = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Russell", "Crowe", "1970-01-20")
                else -> ActorDTO(0L, "러셀", "크로우", "1970-01-20")
            }
            val edwardNorton = when (tenant) {
                Tenant.ENGLISH -> ActorDTO(0L, "Edward", "Norton", "1975-04-03")
                else -> ActorDTO(0L, "에드워드", "노튼", "1975-04-03")
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
                edwardNorton
            )

            val movies = listOf(
                MovieWithActorDTO(
                    0L,
                    if (tenant == Tenant.KOREAN) "글래디에이터" else "Gladiator",
                    johnnyDepp.firstName,
                    "2000-05-01",
                    mutableListOf(russellCrowe, ellenPaige, craigDaniel)
                ),
                MovieWithActorDTO(
                    0L,
                    if (tenant == Tenant.KOREAN) "가디언스 오브 갤럭시" else "Guardians of the galaxy",
                    johnnyDepp.firstName,
                    "2014-07-21",
                    mutableListOf(angelinaGrace, bradPitt, ellenPaige, angelinaJolie, johnnyDepp)
                ),
                MovieWithActorDTO(
                    0L,
                    if (tenant == Tenant.KOREAN) "싸움 클럽" else "Fight club",
                    craigDaniel.firstName,
                    "1999-09-13",
                    mutableListOf(bradPitt, jenniferAniston, edwardNorton)
                ),
                MovieWithActorDTO(
                    0L,
                    if (tenant == Tenant.KOREAN) "13가지 이유" else "13 Reasons Why",
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
                        .where { (ActorTable.firstName eq actor.firstName) and (ActorTable.lastName eq actor.lastName) }
                        .first()[ActorTable.id]

                    ActorInMovieTable.insert {
                        it[ActorInMovieTable.actorId] = actorId.value
                        it[ActorInMovieTable.movieId] = movieId.value
                    }
                }
            }
        }
    }
}
