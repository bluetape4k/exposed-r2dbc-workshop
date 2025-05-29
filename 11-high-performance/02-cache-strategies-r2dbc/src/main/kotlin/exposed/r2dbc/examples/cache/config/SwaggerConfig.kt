package exposed.r2dbc.examples.cache.config

import io.bluetape4k.support.uninitialized
import io.bluetape4k.support.unsafeLazy
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger 설정을 위한 Configuration
 */
@Configuration
class SwaggerConfig {

    @Autowired
    private val buildProps: BuildProperties = uninitialized()

    @Bean
    fun apiInfo(): OpenAPI {
        return OpenAPI().info(info)
    }

    private val info by unsafeLazy {
        Info().title(buildProps.name)
            .description("Spring Webflux 에서 Exposed R2dbc + Redisson Cache 를 이용한 다양한 캐시 전략 예제")
            .version(buildProps.version)
            .contact(contact)
            .license(license)
    }

    private val contact by unsafeLazy {
        Contact()
            .name("Exposed Workshop")
            .email("sunghyouk.bae@gmail.com")
            .url("https://github.com/bluetape4k/exposed-r2dbc-workshop")
    }

    private val license by unsafeLazy {
        License()
            .name("Bluetape4k License 1.0")
            .url("https://bluetape4k.io/license")
    }
}
