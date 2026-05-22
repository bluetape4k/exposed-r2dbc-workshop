package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertFailsWith

class TenantIdResolverTest {

    @ParameterizedTest
    @ValueSource(strings = ["korean", " english "])
    fun `registered tenant id is normalized`(rawTenantId: String) {
        val expected = rawTenantId.trim()

        TenantIdResolver.resolve(rawTenantId) shouldBeEqualTo expected
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   ", "bad tenant", "bad/tenant", "bad\u0001tenant", "unknown-tenant"])
    fun `invalid tenant id returns bad request without raw value`(rawTenantId: String?) {
        val error = assertFailsWith<ResponseStatusException> {
            TenantIdResolver.resolve(rawTenantId)
        }

        error.statusCode shouldBeEqualTo HttpStatus.BAD_REQUEST
        if (!rawTenantId.isNullOrEmpty()) {
            (error.reason?.contains(rawTenantId) ?: false) shouldBeEqualTo false
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])
    fun `too long tenant id returns bad request`(rawTenantId: String) {
        val error = assertFailsWith<ResponseStatusException> {
            TenantIdResolver.resolve(rawTenantId)
        }

        error.statusCode shouldBeEqualTo HttpStatus.BAD_REQUEST
    }
}
