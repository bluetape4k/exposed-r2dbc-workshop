package exposed.r2dbc.examples.cache.caffeine.service

import exposed.r2dbc.examples.cache.caffeine.domain.CacheHealthResponse
import exposed.r2dbc.examples.cache.caffeine.domain.CacheReadStatus
import exposed.r2dbc.examples.cache.caffeine.domain.ProductCaffeineRepository
import exposed.r2dbc.examples.cache.caffeine.domain.ProductReadResponse
import exposed.r2dbc.examples.cache.caffeine.domain.ProductRecord
import exposed.r2dbc.examples.cache.caffeine.domain.UpdateProductRequest
import kotlinx.coroutines.CancellationException

/** provider repository를 HTTP에서 사용할 수 있는 상품 cache service로 감쌉니다. */
class ProductCacheService(
    private val repository: ProductCaffeineRepository,
) {

    suspend fun get(sku: String): ProductReadResponse {
        requireValidSku(sku)
        val status = if (repository.cache.synchronous().getIfPresent(sku) != null) {
            CacheReadStatus.HIT
        } else {
            CacheReadStatus.MISS
        }
        val product = repository.get(sku) ?: throw ProductNotFoundException(sku)
        return ProductReadResponse(product = product, cache = status)
    }

    suspend fun update(sku: String, request: UpdateProductRequest): ProductRecord {
        requireValidSku(sku)
        if (request.name.isBlank() || request.name.length > 120) {
            throw InvalidProductRequestException("name must be 1..120 non-blank characters")
        }
        val current = repository.findByIdFromDb(sku) ?: throw ProductNotFoundException(sku)
        val updated = current.copy(name = request.name, version = current.version + 1)
        return try {
            repository.put(sku, updated)
            updated
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            repository.invalidate(sku)
            throw cause
        }
    }

    suspend fun invalidate(sku: String) {
        requireValidSku(sku)
        repository.invalidate(sku)
    }

    suspend fun clear() {
        repository.clear()
    }

    suspend fun findAllFromDb(): List<ProductRecord> = repository.findAllProductsFromDb()

    suspend fun health(): CacheHealthResponse = CacheHealthResponse.from(repository.validateConsistency())

    private fun requireValidSku(sku: String) {
        if (sku.isBlank() || sku.length > 40) {
            throw InvalidProductRequestException("sku must be 1..40 non-blank characters")
        }
    }
}

/** 상품 SKU 또는 요청 payload가 계약을 벗어났습니다. */
class InvalidProductRequestException(message: String): IllegalArgumentException(message)

/** DB에 요청한 SKU가 없습니다. */
class ProductNotFoundException(sku: String): NoSuchElementException("Product not found: $sku")
