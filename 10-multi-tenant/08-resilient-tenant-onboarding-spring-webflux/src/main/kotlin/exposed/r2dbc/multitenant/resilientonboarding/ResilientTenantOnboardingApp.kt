package exposed.r2dbc.multitenant.resilientonboarding

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.Clock

@SpringBootApplication
class ResilientTenantOnboardingApp {

    companion object {
        fun lifecycleClock(): Clock = Clock.systemUTC()
    }
}

fun main(args: Array<String>) {
    runApplication<ResilientTenantOnboardingApp>(*args)
}
