package com.choru.lock.domain

import com.choru.lock.dto.ProductResponse
import com.choru.lock.infrastructure.ProductRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    fun getProduct(productId: Long): ProductResponse {
        val cacheKey = "product:$productId"
        // 1. Redis Cache 조회(Look aside)
        val cachedData = redisTemplate.opsForValue().get(cacheKey)

        if (cachedData != null) {
            // 캐시 적중(Cache Hit) - JSON Map을 ProductResponse로 변환
            return objectMapper.convertValue(cachedData, ProductResponse::class.java)
        }

        // 2. DB조회 (Cache Miss)
        val product = productRepository.findById(productId).orElseThrow {
            IllegalStateException("Product with ID $productId does not exist")
        }

        // 3. Redis에 저장 (Write Through / Write Back 등 전략 중 하나)
        // 여기서는 가장 일반적인 '조회 시 저장' 전략 사용
        // TTL(만료 시간)을 10초로 설정하여 영원히 남는 가비지 데이터 방지
        val response = ProductResponse(product.id!!, product.name, product.price)

        redisTemplate.opsForValue().set(cacheKey, response, 10, TimeUnit.SECONDS)

        return response
    }
}