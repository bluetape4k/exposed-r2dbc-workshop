# 01 Spring Boot: Spring WebFlux with Exposed R2DBC

초보자를 위한 **Spring WebFlux + Exposed R2DBC** 예제입니다.
영화(Movie)와 배우(Actor) 도메인을 다루며, **비동기 REST API**를 Kotlin Coroutines + Exposed R2DBC로 구현하는 흐름을 학습합니다.

---

## 이 모듈에서 배우는 것

- Spring WebFlux + Coroutines 기반 비동기 REST API 구조
- Exposed R2DBC DSL로 데이터 저장/조회 (suspend 함수, Flow)
- 영화-배우 **다대다 관계** 모델링 및 JOIN 쿼리
- `bufferUntilChanged`로 JOIN 결과를 그룹핑하는 패턴
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

## 핵심 구현 패턴

### 1. suspend 함수 기반 Repository

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

    // 전체 조회 - Flow
    fun findAll(): Flow<MovieRecord> =
        MovieTable.selectAll().map { it.toMovieRecord() }

    // 생성 - suspend
    suspend fun create(movie: MovieRecord): MovieRecord {
        val id = MovieTable.insertAndGetId { ... }
        return movie.copy(id = id.value)
    }
}
```

### 2. `bufferUntilChanged`로 JOIN 결과 그룹핑

영화-배우 다대다 조인 결과를 영화 기준으로 그룹핑합니다.

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

### 3. suspend Controller

Spring WebFlux의 `suspend` 핸들러로 비동기 API를 구현합니다.

```kotlin
@RestController
@RequestMapping("/movies")
class MovieController(private val movieRepository: MovieRepository) {

    @GetMapping
    suspend fun getAllMovies(): List<MovieRecord> =
        suspendTransaction { movieRepository.findAll().toList() }

    @GetMapping("/{id}")
    suspend fun getMovieWithActors(@PathVariable id: Long): MovieWithActorRecord? =
        suspendTransaction { movieRepository.getMovieWithActors(id) }

    @PostMapping
    suspend fun createMovie(@RequestBody movie: MovieRecord): MovieRecord =
        suspendTransaction { movieRepository.create(movie) }
}
```

---

## API 엔드포인트

### Movies (`/movies`)

| Method   | Path                                       | 설명               |
|----------|--------------------------------------------|------------------|
| `GET`    | `/movies`                                  | 전체 영화 목록 조회      |
| `GET`    | `/movies/{id}`                             | 영화 상세 조회 (배우 포함) |
| `GET`    | `/movies/search?name=...&producerName=...` | 영화 검색            |
| `POST`   | `/movies`                                  | 영화 등록            |
| `DELETE` | `/movies/{id}`                             | 영화 삭제            |

### Actors (`/actors`)

| Method   | Path                                        | 설명          |
|----------|---------------------------------------------|-------------|
| `GET`    | `/actors`                                   | 전체 배우 목록 조회 |
| `GET`    | `/actors/{id}`                              | 배우 상세 조회    |
| `POST`   | `/actors`                                   | 배우 등록       |
| `DELETE` | `/actors/{id}`                              | 배우 삭제       |

### Movie-Actors (`/movie-actors`)

| Method | Path                             | 설명                |
|--------|----------------------------------|-------------------|
| `GET`  | `/movie-actors`                  | 전체 영화-배우 관계 조회    |
| `GET`  | `/movie-actors/{movieId}`        | 특정 영화의 배우 목록 조회   |
| `GET`  | `/movie-actors/count`            | 영화별 출연 배우 수 조회    |
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

### 테스트 목록

- **MovieControllerTest** - 영화 CRUD API 통합 테스트
- **ActorControllerTest** - 배우 CRUD API 통합 테스트
- **MovieActorsControllerTest** - 영화-배우 관계 API 통합 테스트
- **MovieRepositoryTest** - 영화 Repository 단위 테스트
- **ActorRepositoryTest** - 배우 Repository 단위 테스트
- **DomainSQLTest** - 도메인 SQL 쿼리 검증
- **ConfigurationTest** - Spring 설정 로드 검증

### 부하 테스트 (Gatling)

```bash
# 영화 API 부하 테스트
./gradlew :spring-webflux-exposed:gatlingRun-MovieSimulation

# 배우 API 부하 테스트
./gradlew :spring-webflux-exposed:gatlingRun-ActorSimulation
```

---

## 참고 자료

- [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Exposed R2DBC](https://github.com/JetBrains/Exposed)
- [Kotlin Exposed Book](https://debop.notion.site/Kotlin-Exposed-Book-1ad2744526b080428173e9c907abdae2)
