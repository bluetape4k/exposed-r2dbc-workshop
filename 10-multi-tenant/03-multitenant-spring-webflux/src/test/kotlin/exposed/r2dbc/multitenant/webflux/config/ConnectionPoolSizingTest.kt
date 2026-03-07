package exposed.r2dbc.multitenant.webflux.config

import io.bluetape4k.utils.Runtimex
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ConnectionPoolSizingTest {

    @Test
    fun `명시된 풀 크기가 있으면 그대로 사용한다`() {
        resolvePoolMaxSize(24) shouldBeEqualTo 24
    }

    @Test
    fun `풀 크기 기본값은 CPU 기반이되 최소 16을 보장한다`() {
        resolvePoolMaxSize(0) shouldBeEqualTo maxOf(Runtimex.availableProcessors * 2, 16)
    }
}
