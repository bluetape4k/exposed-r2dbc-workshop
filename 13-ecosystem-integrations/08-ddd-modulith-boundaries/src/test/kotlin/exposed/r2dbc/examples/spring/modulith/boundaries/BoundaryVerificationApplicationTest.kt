package exposed.r2dbc.examples.spring.modulith.boundaries

import com.tngtech.archunit.core.importer.ImportOption
import exposed.r2dbc.examples.spring.modulith.boundaries.invalid.InvalidBoundaryApplication
import exposed.r2dbc.examples.spring.modulith.boundaries.orders.AcceptOrderCommand
import exposed.r2dbc.examples.spring.modulith.boundaries.orders.OrderApplicationService
import exposed.r2dbc.examples.spring.modulith.boundaries.orders.internal.ExposedOrderRepository
import exposed.r2dbc.examples.spring.modulith.boundaries.shipping.ShippingReservation
import exposed.r2dbc.examples.spring.modulith.boundaries.shipping.ShippingReservationHandler
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.core.Violations

@SpringBootTest(
    classes = [BoundaryVerificationApplication::class],
    properties = [
        "workshop.r2dbc.database-name=ddd-modulith-boundaries-test",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoundaryVerificationApplicationTest @Autowired constructor(
    private val orderService: OrderApplicationService,
    private val orderRepository: ExposedOrderRepository,
    private val shippingHandler: ShippingReservationHandler,
) {

    @BeforeEach
    fun resetTables() = runTest {
        shippingHandler.clear()
        orderRepository.clear()
    }

    @Test
    fun `application modules allow shipping to depend only on order events`() {
        ApplicationModules.of(BoundaryVerificationApplication::class.java).verify()
    }

    @Test
    fun `boundary verifier rejects shipping dependency on order internals`() {
        val violations = assertFailsWith<Violations> {
            ApplicationModules
                .of(InvalidBoundaryApplication::class.java, ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .verify()
        }

        violations.messages.joinToString("\n") shouldContain "orders"
        violations.messages.joinToString("\n") shouldContain "internal"
    }

    @Test
    fun `domain event hands off order acceptance to shipping context`() = runTest {
        val order = orderService.accept(
            AcceptOrderCommand(orderKey = "order-ddd-001", customerId = "customer-42")
        )

        order.status shouldBeEqualTo "ACCEPTED"
        val reservation = awaitReservation(order.orderKey).shouldNotBeNull()
        reservation.orderKey shouldBeEqualTo order.orderKey
        reservation.customerId shouldBeEqualTo order.customerId
    }

    private suspend fun awaitReservation(orderKey: String): ShippingReservation? {
        var reservation: ShippingReservation? = null
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                while (reservation == null) {
                    reservation = shippingHandler.findReservation(orderKey)
                    if (reservation == null) delay(25L)
                }
            }
        }
        return reservation
    }
}
