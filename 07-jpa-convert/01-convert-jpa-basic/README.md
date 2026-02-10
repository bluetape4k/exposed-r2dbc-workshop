# 01-convert-jpa-basic

JPA의 기본 패턴(Entity, 관계 매핑,
`@Convert` 등)을 Exposed R2DBC DSL로 변환하는 방법을 보여주는 예제 모듈입니다. Simple Entity, Blog(1:1, 1:N, N:M 관계), Person(Many-to-One + CRUD 심화), Task(Enum 매핑), Custom Column Type(value class)까지 JPA에서 흔히 사용하는 기본 패턴들을 Exposed로 어떻게 구현하는지 단계별로 학습할 수 있습니다.

## 기술 스택

| 구분   | 기술                                              |
|------|-------------------------------------------------|
| ORM  | Exposed R2DBC + Exposed DAO + Exposed JDBC      |
| 비동기  | Kotlin Coroutines                               |
| DB   | H2 (기본), MariaDB, MySQL 8, PostgreSQL           |
| 컨테이너 | Testcontainers                                  |
| 테스트  | JUnit 5 + Kluent + ParameterizedTest (멀티 DB 지원) |

## 프로젝트 구조

```
src/test/kotlin/exposed/r2dbc/examples/jpa/
├── ex01_simple/
│   ├── SimpleSchema.kt          # 단순 엔티티: Table + Entity(DAO) + DTO + Mapper
│   └── Ex01_Simple_DSL.kt       # DSL 기본 CRUD: batchInsert, select, limit, inList, projection
│
├── ex02_entities/
│   ├── BlogSchema.kt            # 블로그 스키마: Post, PostDetail(1:1), PostComment(1:N), Tag(N:M)
│   ├── Ex01_Blog.kt             # 블로그 테이블 생성, One-to-One 관계 insert/select
│   ├── PersonSchema.kt          # Person-Address 스키마: Many-to-One 관계 + INSERT SELECT용 DML 테이블
│   ├── Ex02_Person.kt           # Person CRUD 심화: count, delete, insert, batchInsert, INSERT SELECT
│   └── Ex03_Task.kt             # Enum 컬럼 매핑 (enumerationByName)
│
└── ex03_customId/
    ├── CustomColumnTypes.kt     # value class 기반 커스텀 컬럼 타입 (Email, Ssn)
    └── Ex01_CustomId.kt         # 커스텀 타입을 PK 및 컬럼으로 사용하는 예제
```

> **참고**: 이 모듈은 `src/main`이 없고, 모든 코드가 `src/test`에 위치합니다. 학습/실습 목적의 테스트 전용 모듈입니다.

## 예제 상세

### ex01_simple - 기본 CRUD (DSL)

JPA의 가장 단순한 `@Entity` + `@Id` + `@Column`을 Exposed로 변환하는 예제입니다.

**JPA 대응 관계:**

| JPA                      | Exposed                                            |
|--------------------------|----------------------------------------------------|
| `@Entity` + `@Table`     | `object SimpleTable: LongIdTable("simple_entity")` |
| `@Id @GeneratedValue`    | `LongIdTable`의 자동 생성 id                            |
| `@Column(unique=true)`   | `varchar("name", 255).uniqueIndex()`               |
| `@Column(nullable=true)` | `text("description").nullable()`                   |
| Entity class             | `LongEntity` (DAO) 또는 `data class` (DTO)           |

**핵심 포인트:**

- **DSL**: SQL에 가까운 타입 안전 쿼리 빌더, `ResultRow`를 직접 다룸
- **DAO**: JPA Entity와 유사한 객체 지향 접근, 자동 변경 감지(dirty checking) 지원
- **Record**: `data class` 기반 DTO, 불변 객체로 캐시나 전송에 적합

```kotlin
// Table 정의 (JPA의 @Entity + @Table)
object SimpleTable: LongIdTable("simple_entity") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").nullable()
}

// Entity 정의 (JPA의 Entity 클래스)
class SimpleEntity(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<SimpleEntity>(SimpleTable)

    var name by SimpleTable.name
    var description by SimpleTable.description
}

// Record 정의 (JPA의 DTO Projection)
data class SimpleRecord(val id: Long, val name: String, val description: String?)
```

**테스트 내용:**

- `batchInsert` - 대량 데이터 삽입
- `select` + `limit` + `offset` - 페이징 조회
- `inList` - IN 절 조회
- `ResultRow` → DTO 변환 (projection)

### ex02_entities - 복합 엔티티 및 관계 매핑

#### Blog 도메인 (1:1, 1:N, N:M)

Post, PostDetail, PostComment, Tag 등 여러 엔티티 간의 관계를 정의합니다.

**스키마 구조:**

```
posts (1) ←──→ (1) post_details        (One-to-One: 공유 PK)
posts (1) ←──→ (N) post_comments       (One-to-Many: FK 참조)
posts (N) ←──→ (N) tags                (Many-to-Many: post_tags 중간 테이블)
```

**JPA vs Exposed 관계 매핑:**

```kotlin
// JPA: @OneToOne + 공유 PK
// Exposed: backReferencedOn (1:1 역방향 참조)
val details: PostDetail by PostDetail backReferencedOn PostDetailTable.id

// JPA: @OneToMany(mappedBy = "post")
// Exposed: referrersOn (1:N 참조)
val comments: SizedIterable<PostComment> by PostComment referrersOn PostCommentTable.postId

// JPA: @ManyToMany + @JoinTable
// Exposed: via (M:N 중간 테이블)
val tags: SizedIterable<Tag> by Tag via PostTagTable
```

**테스트 내용 (Ex01_Blog):**

- 블로그 테이블 생성 및 `exists()` 검증
- One-to-One 관계 insert (Post + PostDetail 공유 PK)

#### Person 도메인 (Many-to-One + CRUD 심화)

Person-Address 관계 CRUD와 다양한 SQL 패턴을 Exposed DSL로 구현합니다.

**테스트 내용 (Ex02_Person):**

- `count` / `countDistinct` - 집계 함수
- `deleteWhere` - 조건부 삭제 (`AND`, `OR`, `LIMIT` 다양한 조합)
- `insertAndGetId` - 단건 삽입 후 ID 반환
- `batchInsert` - DTO(`PersonRecord`) 기반 대량 삽입
- `insert(select(...))` - INSERT SELECT 패턴
- `PersonTableDML` - AutoIncrement 없이 같은 물리 테이블을 참조하여 ID 직접 지정 (INSERT SELECT에서 `id + 100` 패턴)

#### Task (Enum 매핑)

JPA의 `@Enumerated(EnumType.STRING)`을 Exposed의 `enumerationByName`으로 변환합니다.

```kotlin
// JPA
@Enumerated(EnumType.STRING)
@Column(length = 10)
val status: TaskStatusType

// Exposed
val status = enumerationByName("status", 10, TaskStatusType::class)
```

### ex03_customId - 사용자 정의 컬럼 타입 (JPA @Convert)

JPA의 `@Convert` + `AttributeConverter`를 Exposed의 `ColumnWithTransform`으로 변환합니다. Kotlin
`value class`를 활용하여 타입 안전성을 제공합니다.

**JPA 대응 관계:**

| JPA                                          | Exposed                                |
|----------------------------------------------|----------------------------------------|
| `@Convert(converter = EmailConverter.class)` | `email("email_id")` (커스텀 컬럼 함수)        |
| `AttributeConverter<String, Email>`          | `ColumnTransformer<String, Email>`     |
| `@Embeddable` value type                     | `value class Email(val value: String)` |

**커스텀 타입 정의:**

```kotlin
// value class로 타입 안전성 확보
@JvmInline
value class Email(val value: String): Comparable<Email>, Serializable

// 커스텀 컬럼 타입 등록 함수
fun Table.email(name: String, length: Int = 64): Column<Email> =
    registerColumn(name, EmailColumnType(length))

// ColumnWithTransform으로 DB↔Kotlin 타입 변환
open class EmailColumnType(length: Int = 64):
    ColumnWithTransform<String, Email>(VarCharColumnType(length), StringToEmailTransformer())
```

**커스텀 타입을 PK로 사용:**

```kotlin
object CustomIdTable: IdTable<Email>("emails") {
    override val id: Column<EntityID<Email>> = email("email_id").entityId()
    val name = varchar("name", 255)
    val ssn = ssn("ssn").uniqueIndex()   // Ssn도 커스텀 타입
}
```

## JPA vs Exposed 주요 개념 매핑 요약

| JPA                          | Exposed DSL (R2DBC)     | Exposed DAO                         |
|------------------------------|-------------------------|-------------------------------------|
| `@Entity`                    | `Table` / `IdTable`     | `Entity` + `EntityClass`            |
| `@Id` + `@GeneratedValue`    | `LongIdTable`           | `LongEntity`                        |
| `@Column`                    | `varchar()`, `text()` 등 | `var name by Table.name`            |
| `@Enumerated(STRING)`        | `enumerationByName()`   | `enumerationByName()`               |
| `@Convert`                   | Custom `ColumnType`     | Custom `ColumnType`                 |
| `@OneToOne`                  | `reference()`           | `referencedOn` / `backReferencedOn` |
| `@OneToMany`                 | FK `reference()`        | `referrersOn`                       |
| `@ManyToOne`                 | `reference()`           | `referencedOn`                      |
| `@ManyToMany` + `@JoinTable` | 중간 테이블 정의               | `via`                               |
| `EntityManager.persist()`    | `Table.insert {}`       | `Entity.new {}`                     |
| `EntityManager.find()`       | `Table.selectAll()`     | `Entity.findById()`                 |
| JPQL / Criteria API          | DSL 체이닝                 | `Entity.find {}`                    |
| `@UniqueConstraint`          | `.uniqueIndex()`        | `.uniqueIndex()`                    |

## 테스트 실행

```bash
./gradlew :07-jpa-convert:01-convert-jpa-basic:test
```

모든 테스트는 `@ParameterizedTest` + `@MethodSource`로 H2, MySQL, PostgreSQL 등 여러 DB에서 자동 실행됩니다.

## Further Reading

- [9.1 JPA 기본기능 구현하기](https://debop.notion.site/1c32744526b080458ca0f7eee791cab3?v=1c32744526b081ca8b00000c231b9b43)
