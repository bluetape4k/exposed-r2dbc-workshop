package exposed.r2dbc.examples.springbootrepository.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Product 생성에 허용하는 외부 입력입니다. 식별자는 서버가 생성하므로 요청에
 * 노출하지 않고, 저장소 column 길이와 같은 경계를 HTTP 계층에서 먼저 검증합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
data class ProductCreateRequest(
    /** 저장할 Product 이름입니다. */
    @field:NotBlank(message = "name은 비어 있을 수 없습니다")
    @field:Size(max = 120, message = "name은 120자 이하여야 합니다")
    val name: String,
    /** 저장할 Product의 선택적 설명입니다. */
    @field:Size(max = 500, message = "description은 500자 이하여야 합니다")
    val description: String?,
)
