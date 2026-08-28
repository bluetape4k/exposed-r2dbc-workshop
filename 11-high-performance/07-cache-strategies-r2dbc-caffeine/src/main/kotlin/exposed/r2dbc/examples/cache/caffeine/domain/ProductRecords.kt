package exposed.r2dbc.examples.cache.caffeine.domain

import io.bluetape4k.exposed.cache.CacheHealthReport
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/** DB 행과 Caffeine 값, JSON payload가 공유하는 상품 값입니다. */
@Serializable
data class ProductRecord(
    val sku: String,
    val name: String,
    val version: Int,
): JavaSerializable

/** 상품 이름을 변경하는 HTTP 요청입니다. SKU는 path에서 받습니다. */
@Serializable
data class UpdateProductRequest(
    val name: String,
)

/** read-through 요청에서 관찰한 캐시 결과입니다. */
@Serializable
enum class CacheReadStatus {
    HIT,
    MISS,
}

/** 상품과 read-through 결과를 함께 반환합니다. */
@Serializable
data class ProductReadResponse(
    val product: ProductRecord,
    val cache: CacheReadStatus,
)

/** provider health report를 HTTP로 노출하는 안정적인 payload입니다. */
@Serializable
data class CacheHealthResponse(
    val mode: String,
    val queueDepth: Int,
    val workerState: String,
    val lastFlushError: String? = null,
) {
    companion object {
        fun from(report: CacheHealthReport): CacheHealthResponse = CacheHealthResponse(
            mode = report.mode.name,
            queueDepth = report.queueDepth,
            workerState = report.workerState.name,
            lastFlushError = report.lastFlushError?.message ?: report.lastFlushError?.javaClass?.simpleName,
        )
    }
}

/** HTTP 오류에서 stack trace를 숨기기 위한 공통 응답입니다. */
@Serializable
data class StructuredError(
    val code: String,
    val message: String,
)
