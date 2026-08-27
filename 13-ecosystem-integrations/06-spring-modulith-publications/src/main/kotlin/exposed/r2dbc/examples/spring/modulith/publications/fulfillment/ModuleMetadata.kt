package exposed.r2dbc.examples.spring.modulith.publications.fulfillment

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** fulfillment은 orders의 events named interface에만 의존합니다. */
@ApplicationModule(allowedDependencies = ["orders :: events"])
@PackageInfo
class ModuleMetadata
