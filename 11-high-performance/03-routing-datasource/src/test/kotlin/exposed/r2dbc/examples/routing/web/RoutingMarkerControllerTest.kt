package exposed.r2dbc.examples.routing.web

import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.spring.tests.httpGet
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

/**
 * 라우팅 마커 API의 테넌트/읽기전용 분기 동작을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutingMarkerControllerTest(
    @param:Autowired private val client: WebTestClient,
) {

    @Test
    fun `기본 tenant의 read-write 마커를 조회한다`() = runSuspendIO {
        val response = client
            .httpGet("/routing/marker")
            .expectStatus().is2xxSuccessful
            .expectBody<RoutingMarkerResponse>()
            .returnResult()
            .responseBody!!

        response.tenant shouldBeEqualTo "default"
        response.readOnly shouldBeEqualTo false
        response.marker shouldBeEqualTo "default-rw"
    }

    @Test
    fun `acme tenant의 read-only 마커를 조회한다`() = runSuspendIO {
        val response = client
            .get()
            .uri("/routing/marker/readonly")
            .header(TenantRoutingWebFilter.TENANT_HEADER, "acme")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody<RoutingMarkerResponse>()
            .returnResult()
            .responseBody!!

        response.tenant shouldBeEqualTo "acme"
        response.readOnly shouldBeEqualTo true
        response.marker shouldBeEqualTo "acme-ro"
    }

    @Test
    fun `tenant 헤더 미지정은 기본 tenant 지정과 동일하다`() = runSuspendIO {
        val withoutHeader = client
            .httpGet("/routing/marker")
            .expectStatus().is2xxSuccessful
            .expectBody<RoutingMarkerResponse>()
            .returnResult()
            .responseBody!!

        val withDefaultHeader = client
            .get()
            .uri("/routing/marker")
            .header(TenantRoutingWebFilter.TENANT_HEADER, "default")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody<RoutingMarkerResponse>()
            .returnResult()
            .responseBody!!

        withoutHeader.tenant shouldBeEqualTo withDefaultHeader.tenant
        withoutHeader.marker shouldBeEqualTo withDefaultHeader.marker
    }

    @Test
    fun `마커를 갱신하면 같은 tenant의 read-write 경로에서 변경값이 조회된다`() = runSuspendIO {
        client.patch()
            .uri("/routing/marker")
            .header(TenantRoutingWebFilter.TENANT_HEADER, "acme")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"marker":"acme-rw-updated"}""")
            .exchange()
            .expectStatus().is2xxSuccessful

        val response = client
            .get()
            .uri("/routing/marker")
            .header(TenantRoutingWebFilter.TENANT_HEADER, "acme")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody<RoutingMarkerResponse>()
            .returnResult()
            .responseBody!!

        response.marker shouldBeEqualTo "acme-rw-updated"
    }
}
