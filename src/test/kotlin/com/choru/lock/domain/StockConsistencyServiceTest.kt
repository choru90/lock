package com.choru.lock.domain

import com.choru.lock.infrastructure.StockRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
class StockConsistencyServiceTest @Autowired constructor(
    private val stockService: StockService,
    private val stockRepository: StockRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {

    @BeforeEach
    fun setUp(){
        val stock = stockRepository.saveAndFlush(Stock(productId = 1L, quantity = 100L))

        redisTemplate.delete("stock:${stock.id}")
    }

    @Test
    fun `재고_조회시_캐싱되고_수정시_캐시가_삭제되어_정합성이_유지된다`() {
        val stockId = 1L // 위에서 저장한 ID라고 가정 (Auto Increment 초기화 상태에 따라 다를 수 있음. 실제론 findAll로 가져오는게 안전)
        val targetId = stockRepository.findAll()[0].id!!

        // 1. 첫 번째 조회 (Cache Miss -> DB Access)
        println(">>> 1. 첫 번째 조회 (DB)")
        val qty1 = stockService.getStockQuantity(targetId)
        assertEquals(100L, qty1)

        // 2. 두 번째 조회 (Cache Hit -> Redis Access)
        // 로그나 디버거로 확인해보면 Select 쿼리가 안 나가야 함
        println(">>> 2. 두 번째 조회 (Redis)")
        val qty2 = stockService.getStockQuantity(targetId)
        assertEquals(100L, qty2)

        // 3. 재고 감소 (DB Update & Cache Eviction)
        println(">>> 3. 재고 감소 (DB Update & Cache Del)")
        stockService.decrease(targetId, 1L)

        // 4. 세 번째 조회 (Cache Miss -> DB Access -> Redis Set)
        // 캐시가 지워졌으므로 다시 DB에서 99개를 가져와야 함 (만약 캐시가 안 지워졌으면 100개가 나옴 -> 버그)
        println(">>> 4. 세 번째 조회 (DB Refetch)")
        val qty3 = stockService.getStockQuantity(targetId)

        assertEquals(99L, qty3, "캐시가 삭제되지 않아 이전 데이터(100)를 불러왔거나, DB 갱신이 안됨")

        println(">>> 테스트 성공: 재고 정합성 확인 완료")

    }

}