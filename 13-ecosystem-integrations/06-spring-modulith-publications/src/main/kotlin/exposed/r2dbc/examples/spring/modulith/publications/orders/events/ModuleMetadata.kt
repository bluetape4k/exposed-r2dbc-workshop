package exposed.r2dbc.examples.spring.modulith.publications.orders.events

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/** 주문 이벤트만 다른 bounded context에 공개합니다. */
@NamedInterface("events")
@PackageInfo
class ModuleMetadata
