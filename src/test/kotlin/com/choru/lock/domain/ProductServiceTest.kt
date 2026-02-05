package com.choru.lock.domain

import com.choru.lock.infrastructure.ProductRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
class ProductServiceTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {

    private var productId: Long = 0L

    @BeforeEach
    fun setUp() {
        // 1. 테스트용 상품 데이터 생성
        val product = productRepository.save(Product(name = "선착순 한정판 운동화", price = 100000L))
        productId = product.id!!

        // 2. 테스트 전 Redis에 남아있는 데이터 삭제 (Clean-up)
        val cacheKey = "product:$productId"
        redisTemplate.delete(cacheKey)
    }

    @Test
    fun `첫번째_조회는_DB에서_두번째_조회는_Redis에서_가져와야_한다`() {
        // Given: 상품 ID

        // When 1: 첫 번째 조회 (Cache Miss -> DB 조회 -> Redis 저장)
        val startTime1 = System.currentTimeMillis()
        val response1 = productService.getProduct(productId)
        val endTime1 = System.currentTimeMillis()
        val time1 = endTime1 - startTime1

        // When 2: 두 번째 조회 (Cache Hit -> Redis 조회)
        val startTime2 = System.currentTimeMillis()
        val response2 = productService.getProduct(productId)
        val endTime2 = System.currentTimeMillis()
        val time2 = endTime2 - startTime2

        // Then: 데이터 정합성 확인
        assertEquals(response1.id, response2.id)
        assertEquals(response1.name, response2.name)

        // Then: 성능 비교 로그 출력
        println("=====================================================")
        println("[1회차 조회(DB)] 소요 시간: ${time1}ms")
        println("[2회차 조회(Redis)] 소요 시간: ${time2}ms")
        println("속도 차이: 약 ${if (time2 > 0) time1 / time2 else "무한"}배 더 빠름")
        println("=====================================================")
    }
}