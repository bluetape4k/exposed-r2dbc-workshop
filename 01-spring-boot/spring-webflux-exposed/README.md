# 01 Spring Boot: Spring WebFlux with Exposed R2DBC

초보자를 위한 **Spring WebFlux + Exposed R2DBC** 예제입니다.
영화(Movie)와 배우(Actor) 도메인을 다루며, **비동기 REST API**를 Kotlin Coroutines + Exposed R2DBC로 구현하는 흐름을 학습합니다.

---

## 이 모듈에서 배우는 것

- Spring WebFlux + Coroutines 기반 비동기 REST API 구조
- Exposed R2DBC DSL로 데이터 저장/조회 (`suspend` 함수, `Flow`)
- 영화-배우 **다대다 관계** 모델링 및 JOIN 쿼리
- `bufferUntilChanged`로 JOIN 결과를 그룹핑하는 패턴
- Spring Profile(`h2`, `mysql`, `postgres`)로 멀티 DB 전환
- Gatling을 활용한 부하 테스트

---

## Movie 스키마

![Movie Schema](MovieSchema_Dark.png)

```kotlin
object MovieTable: LongIdTable("movies") {
    val name         = varchar("name", 255).index()
    val producerName = varchar("producer_name", 255).index()
    val releaseDate  = datetime("release_date")
}

object ActorTable: LongIdTable("actors") {
    val firstName = varchar("first_name", 255).index()
    val lastName  = varchar("last_name", 255).index()
    val birthday  = date("birthday").nullable()
}

// 영화-배우 다대다 관계 조인 테이블
object ActorInMovieTable: Table("actors_in_movies") {
    val movieId = reference("movie_id", MovieTable, onDelete = ReferenceOption.CASCADE)
    val actorId = reference("actor_id", ActorTable, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(movieId, actorId)
}
```

---

## 프로젝트 구조

```
src/main/kotlin/exposed/r2dbc/workshop/springwebflux/
├── SpringWebfluxApplication.kt                     # Spring Boot 애플리케이션 진입점
├── config/
│   ├── ExposedR2dbcConfig.kt                       # R2DBC Database 및 ConnectionPool 설정
│   ├── NettyConfig.kt                              # Netty 서버 튜닝
│   └── SwaggerConfig.kt                            # OpenAPI(Swagger) 문서 설정
├── controller/
│   ├── IndexController.kt                          # 빌드 정보 조회 (/)
│   ├── MovieController.kt                          # 영화 CRUD API (/movies)
│   ├── ActorController.kt                          # 배우 CRUD API (/actors)
│   └── MovieActorsController.kt                    # 영화-배우 관계 API (/movie-actors)
├── domain/
│   ├── model/
│   │   ├── MovieSchema.kt                          # Exposed 테이블 정의
│   │   ├── MovieDtos.kt                            # DTO (MovieRecord, ActorRecord 등)
│   │   └── Mappers.kt                              # ResultRow → DTO 변환 확장 함수
│   └── repository/
│       ├── MovieRepository.kt                      # 영화 Repository (suspend + Flow)
│       └── ActorRepository.kt                      # 배우 Repository (suspend + Flow)
└── utils/
    └── DataInitializer.kt                          # 애플리케이션 시작 시 샘플 데이터 삽입

src/gatling/kotlin/
├── MovieSimulation.kt                              # 영화 API 부하 테스트
├── ActorSimulation.kt                              # 배우 API 부하 테스트
└── MovieActorsSimulation.kt                        # 영화-배우 관계 API 부하 테스트
```

---

## Spring WebFlux + Exposed R2DBC 통합 흐름

```
HTTP 요청
    │
    ▼
[RestController] (suspend fun)
    │  suspendTransaction { ... }
    ▼
[Repository] (suspend / Flow)
    │  Exposed R2DBC DSL
    ▼
[R2dbcDatabase] ──► ConnectionPool ──► 실제 DB (H2 / MySQL / PostgreSQL)
```

1. WebFlux가 `suspend` 핸들러를 코루틴으로 실행합니다.
2. Controller에서 `suspendTransaction { }` 블록 안에 Repository 호출을 감쌉니다.
3. Repository는 Exposed DSL(`selectAll`, `insert`, `deleteWhere` 등)을 사용해 쿼리를 생성합니다.
4. `R2dbcDatabase`는 `ConnectionPool`을 통해 비동기 DB 연결을 관리합니다.

---

## R2DBC 설정 (`application.yml` + `ExposedR2dbcConfig`)

### application.yml

```yaml
spring:
  profiles:
    default: "h2"    # h2 | mysql | postgres 를 선택할 수 있습니다.

  exposed:
    generate-ddl: true   # 애플리케이션 시작 시 DDL 자동 생성
    show-sql: true       # 실행 SQL 로깅 활성화

server:
  port: 8080
  shutdown: graceful     # Graceful Shutdown 활성화

app:
  virtualthread:
    enabled: true        # Java 21 가상 스레드 활성화 여부
```

### ExposedR2dbcConfig (Profile별 ConnectionFactory)

```kotlin
@Configuration
class ExposedR2dbcConfig {

    // H2 인메모리 DB (기본 프로파일)
    @Bean @Profile("h2")
    fun h2ConnectionFactoryOptions(): ConnectionFactoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PROTOCOL, "mem")
            .option(ConnectionFactoryOptions.DATABASE, "test")
            .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
            .build()

    // PostgreSQL (Testcontainers 자동 기동)
    @Bean @Profile("postgres")
    fun postgresConnectionFactoryOptions(): ConnectionFactoryOptions {
        val postgres = PostgreSQLServer.Launcher.postgres
        return ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, postgres.host)
            .option(ConnectionFactoryOptions.PORT, postgres.port)
            .option(ConnectionFactoryOptions.USER, postgres.username ?: "test")
            .option(ConnectionFactoryOptions.PASSWORD, postgres.password ?: "test")
            .option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 256)
            .build()
    }

    // MySQL (Testcontainers 자동 기동)
    @Bean @Profile("mysql")
    fun mysqlConnectionFactoryOptions(): ConnectionFactoryOptions { ... }

    // ConnectionPool: 모든 Profile에 공통 적용
    @Bean @Primary
    fun connectionPool(options: ConnectionFactoryOptions): ConnectionPool =
        ConnectionPool(
            ConnectionPoolConfiguration.builder(ConnectionFactories.get(options))
                .maxIdleTime(Duration.ofMinutes(30))
                .initialSize(5)
                .maxSize(max(availableProcessors * 8, 100))
                .validationQuery("SELECT 1")
                .build()
        )

    // Exposed R2dbcDatabase 빈 등록
    @Bean
    fun r2dbcDatabase(pool: ConnectionPool, options: ConnectionFactoryOptions,
                      dispatcher: CoroutineDispatcher): R2dbcDatabase =
        R2dbcDatabase.connect(pool, R2dbcDatabaseConfig { this.dispatcher = dispatcher })
}
```

> **포인트**: H2/MySQL/PostgreSQL 각각의 `@Profile` 빈이 `ConnectionFactoryOptions`를 제공하고,
> `connectionPool` + `r2dbcDatabase` 빈이 이를 공통으로 사용합니다.

---

## 핵심 구현 패턴

### 1. `suspend` 함수 기반 Repository

Exposed R2DBC는 `suspend` 함수와 `Flow`를 직접 반환합니다. `Mono`/`Flux`로 변환 없이 Coroutines 스타일로 작성합니다.

```kotlin
@Repository
class MovieRepository {

    // 단건 조회 - suspend
    suspend fun findById(movieId: Long): MovieRecord? =
        MovieTable.selectAll()
            .where { MovieTable.id eq movieId }
            .firstOrNull()
            ?.toMovieRecord()

    // 전체 조회 - Flow (스트리밍)
    fun findAll(): Flow<MovieRecord> =
        MovieTable.selectAll().map { it.toMovieRecord() }

    // 생성 - suspend, insertAndGetId로 자동 생성 ID 반환
    suspend fun create(movie: MovieRecord): MovieRecord {
        val id = MovieTable.insertAndGetId {
            it[name] = movie.name
            it[producerName] = movie.producerName
            if (movie.releaseDate.isNotBlank()) {
                it[releaseDate] = LocalDateTime.parse(movie.releaseDate)
            }
        }
        return movie.copy(id = id.value)
    }

    // 삭제 - suspend, 영향받은 행 수 반환
    suspend fun deleteById(movieId: Long): Int =
        MovieTable.deleteWhere { MovieTable.id eq movieId }
}
```

### 2. `suspendTransaction`을 사용하는 Controller

모든 DB 접근은 `suspendTransaction { }` 블록 안에서 수행해야 합니다.
WebFlux `suspend` 핸들러와 자연스럽게 결합됩니다.

```kotlin
@RestController
@RequestMapping("/movies")
class MovieController(
    private val movieRepository: MovieRepository,
): CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    companion object: KLoggingChannel()

    @GetMapping("/{id}")
    suspend fun getMovieById(@PathVariable("id") movieId: Long): MovieRecord? =
        suspendTransaction {
            movieRepository.findById(movieId)
        }

    @GetMapping
    suspend fun searchMovies(request: ServerHttpRequest): List<MovieRecord> {
        val params = request.queryParams.map { it.key to it.value.first() }.toMap()
        return suspendTransaction {
            if (params.isEmpty()) movieRepository.findAll().toList()
            else movieRepository.searchMovie(params).toList()
        }
    }

    @PostMapping
    suspend fun createMovie(@RequestBody movie: MovieRecord): MovieRecord =
        suspendTransaction { movieRepository.create(movie) }

    @DeleteMapping("/{id}")
    suspend fun deleteMovie(@PathVariable("id") movieId: Long): Int =
        suspendTransaction { movieRepository.deleteById(movieId) }
}
```

### 3. `bufferUntilChanged`로 JOIN 결과 그룹핑

영화-배우 다대다 조인 결과를 영화 기준으로 그룹핑합니다.
DB에서 정렬된 결과를 스트림으로 받아 키가 바뀔 때마다 묶어서 emit합니다.

```kotlin
fun getAllMoviesWithActors(): Flow<MovieWithActorRecord> =
    MovieActorJoin
        .select(MovieTable.id, MovieTable.name, ..., ActorTable.id, ...)
        .map { row -> row.toMovieRecord() to row.toActorRecord() }
        .bufferUntilChanged { it.first.id }  // 동일 movieId가 바뀔 때마다 emit
        .mapNotNull { pairs ->
            val movie = pairs.first().first
            val actors = pairs.map { it.second }
            movie.toMovieWithActorRecord(actors)
        }
```

생성되는 SQL:

```sql
SELECT movies.id, movies."name", movies.producer_name, movies.release_date,
       actors.id, actors.first_name, actors.last_name, actors.birthday
  FROM movies
         INNER JOIN actors_in_movies ON movies.id = actors_in_movies.movie_id
         INNER JOIN actors ON actors.id = actors_in_movies.actor_id
```

### 4. 조건절 제작자-배우 동시 참여 JOIN

추가 조인 조건(`ON` 절에 커스텀 조건)을 지정하는 패턴:

```kotlin
private val moviesWithActingProducersJoin: Join by lazy {
    MovieTable
        .innerJoin(ActorInMovieTable)
        .innerJoin(
            ActorTable,
            onColumn = { ActorTable.id },
            otherColumn = { ActorInMovieTable.actorId }
        ) {
            // ON 절에 추가 조건: 제작자 이름 = 배우 이름
            MovieTable.producerName eq ActorTable.firstName
        }
}
```

생성되는 SQL:

```sql
SELECT movies."name", actors.first_name, actors.last_name
  FROM movies
         INNER JOIN actors_in_movies ON movies.id = actors_in_movies.movie_id
         INNER JOIN actors ON actors.id = actors_in_movies.actor_id
                          AND (movies.producer_name = actors.first_name)
```

---

## API 엔드포인트

### Movies (`/movies`)

| Method   | Path            | 설명                          |
|----------|-----------------|-------------------------------|
| `GET`    | `/movies`       | 전체 영화 목록 조회 (쿼리 파라미터 검색 지원) |
| `GET`    | `/movies/{id}`  | 영화 단건 조회                 |
| `POST`   | `/movies`       | 영화 등록                      |
| `DELETE` | `/movies/{id}`  | 영화 삭제                      |

쿼리 파라미터 예시: `GET /movies?name=Inception&producerName=Nolan`

### Actors (`/actors`)

| Method   | Path            | 설명                          |
|----------|-----------------|-------------------------------|
| `GET`    | `/actors`       | 전체 배우 목록 조회 (쿼리 파라미터 검색 지원) |
| `GET`    | `/actors/{id}`  | 배우 단건 조회                 |
| `POST`   | `/actors`       | 배우 등록                      |
| `DELETE` | `/actors/{id}`  | 배우 삭제                      |

### Movie-Actors (`/movie-actors`)

| Method | Path                             | 설명                         |
|--------|----------------------------------|------------------------------|
| `GET`  | `/movie-actors/{movieId}`        | 특정 영화의 배우 목록 조회    |
| `GET`  | `/movie-actors/count`            | 영화별 출연 배우 수 집계      |
| `GET`  | `/movie-actors/acting-producers` | 제작자가 직접 출연한 영화 조회 |

---

## 실행 방법

### 기본 실행 (H2 인메모리)

```bash
./gradlew :spring-webflux-exposed:bootRun
```

### Profile 지정 실행

```bash
# PostgreSQL (Testcontainers 자동 실행)
./gradlew :spring-webflux-exposed:bootRun --args='--spring.profiles.active=postgres'

# MySQL (Testcontainers 자동 실행)
./gradlew :spring-webflux-exposed:bootRun --args='--spring.profiles.active=mysql'
```

### Swagger UI

애플리케이션 실행 후 http://localhost:8080/webjars/swagger-ui/index.html 에서 API 문서를 확인할 수 있습니다.

---

## 테스트

```bash
./gradlew :spring-webflux-exposed:test
```

### 테스트 구조

```kotlin
// 통합 테스트 기반 클래스: H2 프로파일로 전체 Spring 컨텍스트 로드
@ActiveProfiles("h2")
@SpringBootTest(
    classes = [SpringWebfluxApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractSpringWebfluxTest {
    companion object: KLoggingChannel() {
        @JvmStatic
        val faker = Fakers.faker
    }
}
```

### 테스트 목록

- **MovieControllerTest** — 영화 CRUD API 통합 테스트 (`WebTestClient` 사용)
- **ActorControllerTest** — 배우 CRUD API 통합 테스트
- **MovieActorsControllerTest** — 영화-배우 관계 API 통합 테스트
- **MovieRepositoryTest** — 영화 Repository 단위 테스트
- **ActorRepositoryTest** — 배우 Repository 단위 테스트
- **DomainSQLTest** — 도메인 SQL 쿼리 검증
- **ConfigurationTest** — Spring 설정 로드 검증

### 부하 테스트 (Gatling)

```bash
# 영화 API 부하 테스트
./gradlew :spring-webflux-exposed:gatlingRun-MovieSimulation

# 배우 API 부하 테스트
./gradlew :spring-webflux-exposed:gatlingRun-ActorSimulation

# 영화-배우 관계 API 부하 테스트
./gradlew :spring-webflux-exposed:gatlingRun-MovieActorsSimulation
```

---

## 참고 자료

- [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Exposed R2DBC](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
