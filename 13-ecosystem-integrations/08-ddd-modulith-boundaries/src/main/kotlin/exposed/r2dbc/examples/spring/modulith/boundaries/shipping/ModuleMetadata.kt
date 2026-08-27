package exposed.r2dbc.examples.spring.modulith.boundaries.shipping

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/** shipping module은 orders의 events named interface만 의존합니다. */
@ApplicationModule(allowedDependencies = ["orders :: events"])
@PackageInfo
class ModuleMetadata
