package exposed.r2dbc.examples.springbootrepository

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles

/**
 * Product repository 예제가 사용하는 Spring Boot 통합 테스트의 공통 기반입니다.
 */
@ActiveProfiles("h2")
@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [ExposedSpringBootR2dbcRepositoryApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
abstract class AbstractProductRepositoryTest : AbstractR2dbcExposedTest()
