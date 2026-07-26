# Resilient Tenant Onboarding PostgreSQL Schema Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 H2 동작을 보존하면서 `postgres` profile에서 공유 PostgreSQL 데이터베이스와 테넌트별 schema를 사용하는 복구 가능한 온보딩 예제를 제공한다.

**Architecture:** 생명주기 repository, provisioner, reconciler, runtime registry는 데이터베이스 중립적인 공통 설정에 둔다. H2와 PostgreSQL은 상호 배타적인 profile 설정으로 registry `R2dbcDatabase`와 `TenantRuntimeResourceFactory`를 제공하며, PostgreSQL factory는 `public` registry와 검증된 `tenant_<id>` schema를 명시적으로 분리한다.

**Tech Stack:** Kotlin 2.3, Spring Boot WebFlux, Exposed R2DBC, R2DBC H2, R2DBC PostgreSQL, bluetape4k Testcontainers, JUnit 5, Kotlin Coroutines.

---

## File map

| Path | Responsibility |
| --- | --- |
| `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts` | PostgreSQL R2DBC와 Testcontainers 의존성을 선언한다. |
| `src/main/kotlin/.../config/ResilientTenantOnboardingConfig.kt` | 공통 lifecycle bean만 구성한다. |
| `src/main/kotlin/.../config/H2TenantOnboardingConfig.kt` | 기본 `h2` profile의 registry database와 factory를 구성한다. |
| `src/main/kotlin/.../config/PostgreSqlTenantOnboardingConfig.kt` | `postgres` profile의 공유 database와 schema factory를 구성한다. |
| `src/main/kotlin/.../tenant/TenantDatabaseProperties.kt` | H2와 PostgreSQL 연결 속성을 각각 바인딩한다. |
| `src/main/kotlin/.../tenant/TenantSchemaName.kt` | 외부 tenant ID를 안전한 PostgreSQL schema 이름으로 변환한다. |
| `src/main/kotlin/.../tenant/PostgreSqlSchemaTenantRuntimeResourceFactory.kt` | schema와 readiness table을 멱등적으로 만들고 probe한다. |
| `src/main/kotlin/.../tenant/TenantRuntimeRegistry.kt` | runtime resource에 tenant schema 정보를 함께 보존한다. |
| `src/main/resources/application.yml` | 기본 profile과 공통 lifecycle 정책을 선언한다. |
| `src/main/resources/application-h2.yml` | H2 registry URL을 선언한다. |
| `src/main/resources/application-postgres.yml` | 환경 변수 기반 PostgreSQL 연결 기본값을 선언한다. |
| `src/test/kotlin/.../tenant/TenantSchemaNameTest.kt` | schema 이름 변환과 길이·문자 검증을 고정한다. |
| `src/test/kotlin/.../config/TenantOnboardingProfileConfigTest.kt` | H2와 PostgreSQL profile bean 선택을 검증한다. |
| `src/test/kotlin/.../tenant/PostgreSqlSchemaTenantRuntimeResourceFactoryTest.kt` | 실제 PostgreSQL schema 생성, probe, 격리, 실패 보존을 검증한다. |
| `src/test/kotlin/.../tenant/PostgreSqlTenantLifecycleRestartIntegrationTest.kt` | 같은 컨테이너에서 Spring 문맥 재시작 뒤 runtime 복구를 검증한다. |
| `README.md`, `README.ko.md` | profile 실행법, schema 배치, 보장 범위를 설명한다. |

경로의 `...`는
`exposed/r2dbc/multitenant/resilientonboarding` 패키지 경로를 뜻한다.

### Task 1: 안전한 tenant schema 이름 계약

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantSchemaName.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantSchemaNameTest.kt`

- [ ] **Step 1: 변환 규칙을 고정하는 실패 테스트 작성**

```kotlin
package exposed.r2dbc.multitenant.resilientonboarding.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class TenantSchemaNameTest {

    @Test
    fun `tenant id is converted to a prefixed PostgreSQL schema name`() {
        TenantSchemaName.from(TenantId("clinic-seoul")).value shouldBeEqualTo "tenant_clinic_seoul"
    }

    @Test
    fun `the longest accepted tenant id remains within the PostgreSQL identifier limit`() {
        TenantSchemaName.from(TenantId("a".repeat(31))).value.length shouldBeEqualTo 38
    }
}
```

- [ ] **Step 2: 테스트가 새 타입 부재로 실패하는지 확인**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*TenantSchemaNameTest' --no-build-cache
```

Expected: `Unresolved reference 'TenantSchemaName'`로 FAIL.

- [ ] **Step 3: 변환과 재검증을 한 타입에 구현**

```kotlin
package exposed.r2dbc.multitenant.resilientonboarding.tenant

@JvmInline
value class TenantSchemaName private constructor(val value: String) {

    companion object {
        private const val PREFIX = "tenant_"
        private const val POSTGRES_IDENTIFIER_LIMIT = 63
        private val SchemaPattern = Regex("[a-z][a-z0-9_]*")

        fun from(tenantId: TenantId): TenantSchemaName {
            val value = PREFIX + tenantId.value.replace('-', '_')
            require(value.length <= POSTGRES_IDENTIFIER_LIMIT) {
                "tenant schema name exceeds PostgreSQL's 63-byte identifier limit"
            }
            require(SchemaPattern.matches(value)) {
                "tenant schema name must contain lowercase letters, digits, or underscores"
            }
            return TenantSchemaName(value)
        }
    }
}
```

`TenantId`의 현재 최대 길이는 31자이므로 정상 입력은 길이 제한을 넘지 않는다.
schema 접두사까지 포함한 최종 방어 검증은 `TenantSchemaName`이 책임지며,
기존 API의 소문자·숫자·하이픈 및 길이 규칙은 변경하지 않는다.

- [ ] **Step 4: 타입 테스트와 기존 lifecycle 타입 테스트 실행**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*TenantSchemaNameTest' \
  --tests '*TenantLifecycleRepositoryTest' \
  --no-build-cache
```

Expected: PASS.

- [ ] **Step 5: schema 이름 계약 커밋**

```bash
git add \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantSchemaName.kt \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantSchemaNameTest.kt
git commit -m "Guard tenant schema identifiers before SQL execution" \
  -m "Constraint: PostgreSQL identifiers are limited to 63 bytes." \
  -m "Rejected: Interpolate raw tenant IDs | API validation alone does not express the SQL identifier contract." \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: Derive every tenant schema through TenantSchemaName." \
  -m "Tested: TenantSchemaNameTest and TenantLifecycleRepositoryTest."
```

### Task 2: H2와 PostgreSQL profile 설정 분리

**Files:**
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/build.gradle.kts`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/ResilientTenantOnboardingConfig.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/H2TenantOnboardingConfig.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/PostgreSqlTenantOnboardingConfig.kt`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantDatabaseProperties.kt`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantLifecycleProperties.kt`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/resources/application.yml`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/resources/application-h2.yml`
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/resources/application-postgres.yml`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/TenantOnboardingProfileConfigTest.kt`

- [ ] **Step 1: profile별 bean 선택 실패 테스트 작성**

```kotlin
package exposed.r2dbc.multitenant.resilientonboarding.config

import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlSchemaTenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlTenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeResourceFactory
import io.bluetape4k.assertions.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class TenantOnboardingProfileConfigTest {

    @Test
    fun `h2 is the default infrastructure profile`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=h2")
            .withUserConfiguration(
                ProfilePropertiesTestConfig::class.java,
                H2TenantOnboardingConfig::class.java,
                PostgreSqlTenantOnboardingConfig::class.java,
            )
            .run { context ->
                context.getBean(TenantRuntimeResourceFactory::class.java)
                    .shouldBeInstanceOf<H2TenantRuntimeResourceFactory>()
            }
    }

    @Test
    fun `postgres profile does not register the H2 factory`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=postgres")
            .withUserConfiguration(
                ProfilePropertiesTestConfig::class.java,
                H2TenantOnboardingConfig::class.java,
                PostgreSqlTenantOnboardingConfig::class.java,
            )
            .run { context ->
                context.getBean(TenantRuntimeResourceFactory::class.java)
                    .shouldBeInstanceOf<PostgreSqlSchemaTenantRuntimeResourceFactory>()
            }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    H2TenantDatabaseProperties::class,
    PostgreSqlTenantDatabaseProperties::class,
)
private class ProfilePropertiesTestConfig
```

PostgreSQL context에는 Task 3의 factory 구현 전까지 최소 생성자만 있는 클래스를
추가해 컴파일시키고, 실제 연결 검증은 Task 3의 컨테이너 테스트가 담당한다.

- [ ] **Step 2: profile 테스트가 설정 클래스 부재로 실패하는지 확인**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*TenantOnboardingProfileConfigTest' --no-build-cache
```

Expected: 새 profile 설정 및 PostgreSQL factory 참조 부재로 FAIL.

- [ ] **Step 3: 필요한 의존성과 profile별 속성 추가**

`build.gradle.kts`의 R2DBC 의존성에 다음을 추가한다.

```kotlin
implementation(libs.r2dbc.postgresql)
testImplementation(libs.bluetape4k.testcontainers)
testImplementation(libs.testcontainers.postgresql)
```

속성은 다음처럼 분리한다.

```kotlin
@ConfigurationProperties("app.resilient-onboarding.h2")
data class H2TenantDatabaseProperties(
    val registryUrl: String =
        "r2dbc:h2:mem:///resilient_tenant_registry;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
)

@ConfigurationProperties("app.resilient-onboarding.postgres")
data class PostgreSqlTenantDatabaseProperties(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "postgres",
    val username: String = "postgres",
    val password: String = "postgres",
) {
    fun connectionFactoryOptions(): ConnectionFactoryOptions =
        ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, host)
            .option(ConnectionFactoryOptions.PORT, port)
            .option(ConnectionFactoryOptions.DATABASE, database)
            .option(ConnectionFactoryOptions.USER, username)
            .option(ConnectionFactoryOptions.PASSWORD, password)
            .option(ConnectionFactoryOptions.SSL, false)
            .build()
}
```

`TenantLifecycleProperties`에서는 `registryUrl`을 제거한다. 공통 설정의
`@EnableConfigurationProperties`에는 세 속성 타입을 모두 등록한다.

- [ ] **Step 4: profile별 registry database와 factory bean 구성**

H2 설정:

```kotlin
@Configuration(proxyBeanMethods = false)
@Profile("h2")
class H2TenantOnboardingConfig {

    @Bean
    fun registryDatabase(properties: H2TenantDatabaseProperties): R2dbcDatabase =
        R2dbcDatabase.connect(
            ConnectionFactories.get(properties.registryUrl),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )

    @Bean
    fun tenantRuntimeResourceFactory(): TenantRuntimeResourceFactory =
        H2TenantRuntimeResourceFactory()
}
```

PostgreSQL 설정:

```kotlin
@Configuration(proxyBeanMethods = false)
@Profile("postgres")
class PostgreSqlTenantOnboardingConfig {

    @Bean
    fun postgresConnectionFactory(
        properties: PostgreSqlTenantDatabaseProperties,
    ): ConnectionFactory =
        ConnectionFactories.get(properties.connectionFactoryOptions())

    @Bean
    fun registryDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactory,
            R2dbcDatabaseConfig { explicitDialect = PostgreSQLDialect() },
        )

    @Bean
    fun tenantRuntimeResourceFactory(
        connectionFactory: ConnectionFactory,
        registryDatabase: R2dbcDatabase,
    ): TenantRuntimeResourceFactory =
        PostgreSqlSchemaTenantRuntimeResourceFactory(connectionFactory, registryDatabase)
}
```

공통 `ResilientTenantOnboardingConfig`에서는 기존 `registryDatabase`와
`tenantRuntimeResourceFactory` bean을 제거한다.

- [ ] **Step 5: profile YAML 분리**

`application.yml`:

```yaml
spring:
  profiles:
    default: h2

app:
  resilient-onboarding:
    lease-duration: PT2M
    overall-provision-timeout: PT1M
    admin-token: ${TENANT_ADMIN_TOKEN:workshop-admin}
```

`application-h2.yml`:

```yaml
app:
  resilient-onboarding:
    h2:
      registry-url: r2dbc:h2:mem:///resilient_tenant_registry;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
```

`application-postgres.yml`:

```yaml
app:
  resilient-onboarding:
    postgres:
      host: ${POSTGRES_HOST:localhost}
      port: ${POSTGRES_PORT:5432}
      database: ${POSTGRES_DATABASE:postgres}
      username: ${POSTGRES_USERNAME:postgres}
      password: ${POSTGRES_PASSWORD:postgres}
```

- [ ] **Step 6: profile 및 기존 Spring context 테스트 실행**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*TenantOnboardingProfileConfigTest' \
  --tests '*ResilientTenantOnboardingAppTest' \
  --no-build-cache
```

Expected: PASS, H2와 PostgreSQL profile에서 factory bean이 하나씩만 존재.

- [ ] **Step 7: profile 경계 커밋**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux
git commit -m "Separate tenant storage behind database profiles" \
  -m "Constraint: H2 remains the default while PostgreSQL uses an explicit profile." \
  -m "Rejected: Branch on URL inside one factory | it hides incompatible dialect and provisioning behavior." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep lifecycle orchestration free of database-specific branches." \
  -m "Tested: TenantOnboardingProfileConfigTest and ResilientTenantOnboardingAppTest."
```

### Task 3: PostgreSQL tenant schema 생성과 probe

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/PostgreSqlSchemaTenantRuntimeResourceFactory.kt`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/main/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/TenantRuntimeRegistry.kt`
- Test: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/PostgreSqlSchemaTenantRuntimeResourceFactoryTest.kt`

- [ ] **Step 1: PostgreSQL 컨테이너 fixture 작성**

```kotlin
private val postgres by lazy { PostgreSQLServer.Launcher.postgres }

private fun connectionOptions(): ConnectionFactoryOptions =
    ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "postgresql")
        .option(ConnectionFactoryOptions.HOST, postgres.host)
        .option(ConnectionFactoryOptions.PORT, postgres.port)
        .option(ConnectionFactoryOptions.DATABASE, postgres.databaseName)
        .option(ConnectionFactoryOptions.USER, postgres.username ?: "test")
        .option(ConnectionFactoryOptions.PASSWORD, postgres.password ?: "test")
        .option(ConnectionFactoryOptions.SSL, false)
        .build()
```

`PostgreSQLServer`의 실제 accessor 이름이 현재 bluetape4k-testcontainers API와
다르면 `10-multi-tenant/03-multitenant-spring-webflux`에서 컴파일되는
`host`, `port`, `username`, `password` 사용법을 기준으로 맞춘다. 데이터베이스
이름은 launcher가 제공하는 값을 사용하고, 제공하지 않으면 `postgres`를 사용한다.

- [ ] **Step 2: schema 생성·probe·격리 실패 테스트 작성**

```kotlin
@Test
fun `create prepares a tenant schema and readiness marker`() = runSuspendIO {
    val resources = factory.create(metadata("clinic-seoul"))

    factory.probe(resources)

    resources.schemaName shouldBeEqualTo TenantSchemaName.from(TenantId("clinic-seoul"))
    queryCurrentSchema(resources) shouldBeEqualTo "tenant_clinic_seoul"
    queryMarkerTenant(resources) shouldBeEqualTo "clinic-seoul"
}

@Test
fun `two tenants use different schemas on the same connection factory`() = runSuspendIO {
    val seoul = factory.create(metadata("clinic-seoul"))
    val busan = factory.create(metadata("clinic-busan"))

    factory.probe(seoul)
    factory.probe(busan)

    (seoul.connectionFactory === busan.connectionFactory) shouldBeEqualTo true
    queryMarkerTenant(seoul) shouldBeEqualTo "clinic-seoul"
    queryMarkerTenant(busan) shouldBeEqualTo "clinic-busan"
}

@Test
fun `closing failed resources does not drop the tenant schema`() = runSuspendIO {
    val resources = factory.create(metadata("clinic-failed"))

    factory.close(resources)

    schemaExists(resources.schemaName).shouldBeTrue()
}
```

- [ ] **Step 3: 테스트가 factory와 schema 정보 부재로 실패하는지 확인**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*PostgreSqlSchemaTenantRuntimeResourceFactoryTest' \
  --no-build-cache
```

Expected: `PostgreSqlSchemaTenantRuntimeResourceFactory` 또는
`TenantResources.schemaName` 부재로 FAIL.

- [ ] **Step 4: runtime resource에 schema 계약 추가**

```kotlin
data class TenantResources(
    val tenantId: TenantId,
    val connectionFactory: ConnectionFactory,
    val schemaName: TenantSchemaName? = null,
)
```

H2 factory는 기본값 `null`을 사용하므로 기존 테스트 동작을 유지한다.

- [ ] **Step 5: PostgreSQL schema factory 최소 구현**

```kotlin
class PostgreSqlSchemaTenantRuntimeResourceFactory(
    private val connectionFactory: ConnectionFactory,
    private val database: R2dbcDatabase,
): TenantRuntimeResourceFactory {

    override suspend fun create(metadata: TenantMetadata): TenantResources {
        val schemaName = TenantSchemaName.from(metadata.tenantId)
        val schema = Schema(schemaName.value)

        suspendTransaction(db = database) {
            SchemaUtils.createSchema(schema)
            SchemaUtils.setSchema(schema)
            SchemaUtils.create(TenantReadinessTable)
            TenantReadinessTable.insertIgnore {
                it[tenantId] = metadata.tenantId.value
            }
        }
        return TenantResources(metadata.tenantId, connectionFactory, schemaName)
    }

    override suspend fun probe(resources: TenantResources) {
        val schemaName = requireNotNull(resources.schemaName) {
            "PostgreSQL tenant resources require a schema name"
        }
        suspendTransaction(db = database) {
            SchemaUtils.setSchema(Schema(schemaName.value))
            check(
                TenantReadinessTable
                    .selectAll()
                    .where { TenantReadinessTable.tenantId eq resources.tenantId.value }
                    .count() == 1L
            ) {
                "Tenant readiness marker is missing"
            }
        }
    }

    override suspend fun close(resources: TenantResources) = Unit
}

private object TenantReadinessTable: Table("tenant_readiness") {
    val tenantId = varchar("tenant_id", 64)
    override val primaryKey = PrimaryKey(tenantId)
}
```

`SchemaUtils.createSchema`와 `SchemaUtils.create`는 존재하는 객체를 보존하는
멱등 동작인지 컨테이너 테스트에서 같은 tenant에 `create`를 두 번 호출해 검증한다.
SQL 문자열을 직접 조합하지 않는다.

- [ ] **Step 6: PostgreSQL factory 테스트 실행**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*PostgreSqlSchemaTenantRuntimeResourceFactoryTest' \
  --no-build-cache
```

Expected: PASS; 동일 connection factory에서 tenant schema와 marker가 분리되고
`close` 뒤에도 schema가 존재.

- [ ] **Step 7: schema factory 커밋**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux
git commit -m "Provision PostgreSQL tenants in isolated schemas" \
  -m "Constraint: Tenant schemas share one database and failed attempts retain durable evidence." \
  -m "Rejected: Drop schemas during cleanup | stale attempts could delete data prepared by a newer owner." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Select the tenant schema inside every database transaction." \
  -m "Tested: PostgreSqlSchemaTenantRuntimeResourceFactoryTest."
```

### Task 4: PostgreSQL lifecycle와 재시작 복구 통합 검증

**Files:**
- Create: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/tenant/PostgreSqlTenantLifecycleRestartIntegrationTest.kt`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test/kotlin/exposed/r2dbc/multitenant/resilientonboarding/config/TenantOnboardingProfileConfigTest.kt`

- [ ] **Step 1: PostgreSQL Spring context 생성 helper 작성**

```kotlin
private fun postgresContext(postgres: PostgreSQLServer) =
    SpringApplicationBuilder(ResilientTenantOnboardingApp::class.java)
        .profiles("postgres")
        .properties(
            "app.resilient-onboarding.postgres.host=${postgres.host}",
            "app.resilient-onboarding.postgres.port=${postgres.port}",
            "app.resilient-onboarding.postgres.database=${postgres.databaseName}",
            "app.resilient-onboarding.postgres.username=${postgres.username}",
            "app.resilient-onboarding.postgres.password=${postgres.password}",
            "spring.main.web-application-type=none",
        )
        .run()
```

launcher가 nullable accessor를 제공하면 테스트 fixture에서 `requireNotNull`로
명시적으로 해제한다. 컨테이너 자격 증명을 로그에 출력하지 않는다.

- [ ] **Step 2: 실제 재시작 복구 실패 테스트 작성**

```kotlin
@Test
fun `active tenant is republished after PostgreSQL application restart`() = runSuspendIO {
    val tenantId = TenantId("restart-postgres")

    postgresContext(postgres).use { beforeRestart ->
        val provisioner = beforeRestart.getBean(ResilientTenantProvisioner::class.java)
        provisioner.onboard(TenantOnboardingCommand(tenantId, "Restart PostgreSQL"))
            .shouldBeInstanceOf<TenantOnboardingResult.Created>()
    }

    postgresContext(postgres).use { afterRestart ->
        val repository = afterRestart.getBean(TenantLifecycleRepository::class.java)
        val registry = afterRestart.getBean(TenantRuntimeRegistry::class.java)

        repository.find(tenantId).shouldNotBeNull().status shouldBeEqualTo
            TenantLifecycleStatus.ACTIVE
        registry.connectionFactoryOrNull(tenantId).shouldNotBeNull()
    }
}
```

- [ ] **Step 3: 불완전한 ACTIVE schema의 복구 실패 테스트 작성**

```kotlin
@Test
fun `active lifecycle row without a ready schema becomes recovery failure`() = runSuspendIO {
    val tenantId = TenantId("missing-schema")

    postgresContext(postgres).use { context ->
        val repository = context.getBean(TenantLifecycleRepository::class.java)
        repository.initializeSchema()
        val owner = repository.claim(tenantId, "Missing Schema", now) as TenantClaim.Owner
        repository.markActive(owner, now.plusSeconds(1)).shouldNotBeNull()
    }

    postgresContext(postgres).use { restarted ->
        restarted.getBean(TenantLifecycleRepository::class.java)
            .find(tenantId)
            .shouldNotBeNull()
            .apply {
                status shouldBeEqualTo TenantLifecycleStatus.FAILED
                lastFailureCode shouldBeEqualTo TenantFailureCode.RECOVERY
            }
        restarted.getBean(TenantRuntimeRegistry::class.java)
            .connectionFactoryOrNull(tenantId)
            .shouldBeNull()
    }
}
```

- [ ] **Step 4: 테스트가 profile 또는 복구 오류로 실패하는지 확인**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*PostgreSqlTenantLifecycleRestartIntegrationTest' \
  --no-build-cache
```

Expected: 첫 구현 결함이 드러나는 지점에서 FAIL. 두 번째 문맥이 시작되기 전에
첫 번째 문맥과 runtime registry가 실제로 닫혔는지 확인한다.

- [ ] **Step 5: 재시작 시 schema-aware resource 재생성 보완**

reconciler의 계약은 변경하지 않는다. factory의 `create(metadata)`가 기존 schema에
멱등적으로 연결하고 `probe(resources)`가 readiness marker를 확인하게 한다. 실패한
probe는 reconciler의 기존 처리로 해당 행을 `RECOVERY` 실패로 바꾼다.

Spring context 종료 시 공유 `ConnectionPool`을 도입했다면 destroy method로 닫고,
driver connection factory만 사용한다면 추가 close bean을 만들지 않는다. runtime
registry는 연결 자원의 소유자가 아니라 준비 완료된 routing 참조의 소유자라는
현재 계약을 유지한다.

- [ ] **Step 6: PostgreSQL 통합 테스트와 H2 재시작 테스트 실행**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test \
  --tests '*PostgreSqlTenantLifecycleRestartIntegrationTest' \
  --tests '*TenantLifecycleRestartIntegrationTest' \
  --no-build-cache
```

Expected: PASS; PostgreSQL과 H2 모두 재시작 뒤 `ACTIVE` tenant를 다시 공개.

- [ ] **Step 7: 재시작 통합 검증 커밋**

```bash
git add 10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src/test
git commit -m "Prove PostgreSQL tenant recovery across restarts" \
  -m "Constraint: Recovery must use durable registry and schema state, not process memory." \
  -m "Rejected: Reinvoke only the reconciler in one context | it does not prove application restart wiring." \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Directive: Keep a full context restart test for every durable backend." \
  -m "Tested: PostgreSqlTenantLifecycleRestartIntegrationTest and TenantLifecycleRestartIntegrationTest."
```

### Task 5: 양언어 문서와 전체 회귀 검증

**Files:**
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md`
- Modify: `10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md`

- [ ] **Step 1: 기존 H2 전용 표현을 찾아 문서 실패 기준 확인**

Run:

```bash
rg -n "not.*PostgreSQL|PostgreSQL.*아닙니다|H2 file|H2 파일" \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md
```

Expected: 현재 PostgreSQL을 지원하지 않는다는 제한 문구가 두 로케일에서 발견됨.

- [ ] **Step 2: README에 profile별 실행과 schema 배치 추가**

영문 README에 다음 실행 계약을 추가한다.

````markdown
## Database profiles

The default `h2` profile keeps the example fast and self-contained:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun
```

The `postgres` profile uses `public.tenant_lifecycle` for durable control-plane
state and creates one `tenant_<tenant-id>` schema per tenant:

```bash
POSTGRES_HOST=localhost \
POSTGRES_PORT=5432 \
POSTGRES_DATABASE=postgres \
POSTGRES_USERNAME=postgres \
POSTGRES_PASSWORD=postgres \
./gradlew :08-resilient-tenant-onboarding-spring-webflux:bootRun \
  --args='--spring.profiles.active=postgres'
```

Failed onboarding attempts keep their schema for diagnosis and idempotent retry.
This module does not implement automatic schema deletion, distributed locking,
external secret management, or production authorization.
````

한국어 README에는 같은 정보 구조와 명령을 유지하되 설명을 자연스러운 한국어로
작성한다. 영문 문장을 기계적으로 직역하지 않는다.

- [ ] **Step 3: README 제한 문구와 로케일 구조 검증**

Run:

```bash
rg -n "Database profiles|데이터베이스 프로필|tenant_lifecycle|tenant_<" \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md
git diff --check
```

Expected: 두 README 모두 profile, registry, tenant schema, 실패 보존 범위를 설명하고
whitespace 오류가 없음.

- [ ] **Step 4: 모듈 전체 테스트 실행**

Run:

```bash
./gradlew :08-resilient-tenant-onboarding-spring-webflux:test --no-build-cache
```

Expected: 기존 11개 테스트와 새 schema/profile/PostgreSQL 테스트가 모두 PASS.

- [ ] **Step 5: 모듈 정적 검증 실행**

Run:

```bash
./gradlew \
  :08-resilient-tenant-onboarding-spring-webflux:compileKotlin \
  :08-resilient-tenant-onboarding-spring-webflux:compileTestKotlin \
  :08-resilient-tenant-onboarding-spring-webflux:detekt \
  --no-build-cache
```

Expected: 모든 task PASS. `detekt` task가 모듈에 없으면 root에서 실제 제공되는
정적 분석 task를 `./gradlew tasks --all | rg 'detekt|lint'`로 확인한 뒤 가장
좁은 모듈 task를 실행하고 결과를 기록한다.

- [ ] **Step 6: 변경 범위와 자격 증명 노출 점검**

Run:

```bash
git diff --check
git diff --stat
rg -n "password.*(println|log)|log.*password|reservationToken.*log" \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/src || true
```

Expected: whitespace 오류 없음, 변경이 08 모듈과 승인된 설계·계획 문서에 국한,
자격 증명 또는 reservation token 로그 없음.

- [ ] **Step 7: 문서 및 최종 검증 커밋**

```bash
git add \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.md \
  10-multi-tenant/08-resilient-tenant-onboarding-spring-webflux/README.ko.md
git commit -m "Document PostgreSQL tenant schema operations" \
  -m "Constraint: Readers need runnable profile commands without overstating production guarantees." \
  -m "Rejected: Replace the H2 path | it remains the fast default and restart teaching fixture." \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Directive: Keep English and Korean profile documentation structurally aligned." \
  -m "Tested: Module test, compile, static analysis, README search, and git diff --check."
```

## Self-review 결과

- 설계의 profile 분리, `public` registry, `tenant_<id>` 변환, 트랜잭션별 schema
  선택, 실패 schema 보존, 재시도, 재시작 복구, 양언어 문서를 각 Task에 연결했다.
- 자동 schema 삭제, database-per-tenant, migration 도구, 다중 노드 제어는 구현
  Task에서 제외했다.
- `TenantSchemaName`, `PostgreSqlTenantDatabaseProperties`,
  `PostgreSqlSchemaTenantRuntimeResourceFactory`, `TenantResources.schemaName` 이름을
  모든 Task에서 동일하게 사용했다.
- 코드 단계에 미정 placeholder를 남기지 않았다.
