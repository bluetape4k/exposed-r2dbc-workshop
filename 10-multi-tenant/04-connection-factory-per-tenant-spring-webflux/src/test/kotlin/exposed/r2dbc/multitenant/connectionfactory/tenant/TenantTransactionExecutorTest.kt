package exposed.r2dbc.multitenant.connectionfactory.tenant

import exposed.r2dbc.multitenant.connectionfactory.AbstractMultitenantTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TenantTransactionExecutorTest(
    @param:Autowired private val transactionExecutor: TenantTransactionExecutor,
): AbstractMultitenantTest() {

    @Test
    fun `cancellation exception propagates from transaction block`() = runSuspendIO {
        assertFailsWith<CancellationException> {
            transactionExecutor.execute {
                throw CancellationException("test cancellation")
            }
        }
    }
}
