package exposed.r2dbc.examples.springbootrepository.domain

/**
 * Exposed row와 API 사이를 연결하는 불변 Product 모델입니다.
 *
 * 새 Product를 저장할 때 [id]는 `null`이고 provider가 생성한 식별자를 반환합니다.
 */
data class ProductRecord(
    /** 새 Product에서 `null`이고 저장 후 provider가 생성하는 식별자입니다. */
    val id: Long?,
    /** Product의 표시 이름입니다. */
    val name: String,
    /** Product의 선택적 설명입니다. */
    val description: String?,
)
