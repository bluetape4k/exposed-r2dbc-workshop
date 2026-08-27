package exposed.r2dbc.examples.spring.modulith.boundaries.orders.events

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/** 주문 bounded context가 외부에 공개하는 named interface입니다. */
@NamedInterface("events")
@PackageInfo
class ModuleMetadata
