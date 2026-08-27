package exposed.r2dbc.examples.spring.modulith.boundaries.invalid.orders.internal

/** shipping이 import하면 안 되는 orders internal fixture입니다. */
class LeakyOrderRepository {
    fun exists(orderKey: String): Boolean = orderKey.isNotBlank()
}
