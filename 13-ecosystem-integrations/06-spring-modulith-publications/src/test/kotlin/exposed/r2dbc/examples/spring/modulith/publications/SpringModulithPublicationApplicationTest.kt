package exposed.r2dbc.examples.spring.modulith.publications

import exposed.r2dbc.examples.spring.modulith.publications.fulfillment.FulfillmentReservationHandler
import exposed.r2dbc.examples.spring.modulith.publications.fulfillment.R2dbcPublicationDispatcher
import exposed.r2dbc.examples.spring.modulith.publications.orders.ApproveOrderCommand
import exposed.r2dbc.examples.spring.modulith.publications.orders.OrderApplicationService
import exposed.r2dbc.examples.spring.modulith.publications.orders.events.PublicationStatus
import exposed.r2dbc.examples.spring.modulith.publications.orders.internal.ExposedOrderRepository
import exposed.r2dbc.examples.spring.modulith.publications.orders.internal.R2dbcPublicationLog
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.modulith.core.ApplicationModules

@SpringBootTest(
    classes = [SpringModulithPublicationApplication::class],
    properties = [
        "workshop.r2dbc.database-name=spring-modulith-publications-test",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringModulithPublicationApplicationTest @Autowired constructor(
    private val orderService: OrderApplicationService,
    private val orderRepository: ExposedOrderRepository,
    private val fulfillmentHandler: FulfillmentReservationHandler,
    private val publicationLog: R2dbcPublicationLog,
    private val dispatcher: R2dbcPublicationDispatcher,
) {

    @BeforeEach
    fun resetTables() = runTest {
        orderRepository.clear()
        fulfillmentHandler.clear()
    }

    @Test
    fun `spring modulith modules expose only the orders events boundary`() {
        ApplicationModules.of(SpringModulithPublicationApplication::class.java).verify()
    }

    @Test
    fun `order and publication log are committed in one r2dbc flow`() = runTest {
        val order = orderService.approve(
            ApproveOrderCommand(orderKey = "order-complete", customerId = "customer-a")
        )

        order.status shouldBeEqualTo "APPROVED"
        val outstanding = publicationLog.findOutstanding()
        outstanding.size shouldBeEqualTo 1
        outstanding.single().event.orderKey shouldBeEqualTo order.orderKey

        dispatcher.dispatchOutstanding().completed shouldBeEqualTo 1
        val reservation = fulfillmentHandler.findReservation(order.orderKey).shouldNotBeNull()
        reservation.customerId shouldBeEqualTo order.customerId
        publicationLog.findByStatus(PublicationStatus.COMPLETED.name).size shouldBeEqualTo 1
    }

    @Test
    fun `failed publication is inspectable and can be retried`() = runTest {
        fulfillmentHandler.failNextReservation()
        val order = orderService.approve(
            ApproveOrderCommand(orderKey = "order-retry", customerId = "customer-b")
        )

        dispatcher.dispatchOutstanding().failed shouldBeEqualTo 1
        val failed = publicationLog.findByStatus(PublicationStatus.FAILED.name).single()
        failed.attempts shouldBeEqualTo 1
        failed.lastError shouldBeEqualTo "Simulated downstream reservation failure for ${order.orderKey}"

        dispatcher.dispatchOutstanding().completed shouldBeEqualTo 1
        fulfillmentHandler.findReservation(order.orderKey).shouldNotBeNull()
        val completed = publicationLog.findByStatus(PublicationStatus.COMPLETED.name).single()
        completed.attempts shouldBeEqualTo 2
    }
}
