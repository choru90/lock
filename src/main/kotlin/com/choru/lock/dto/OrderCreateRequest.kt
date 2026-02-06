package com.choru.lock.dto

import java.util.UUID

data class OrderCreateRequest(
    val userId: Long,
    val productId: Long,
    val couponId: Long,
    // 추후 대기열 순번 확인용 UUID등이 추가될 수 있음
    val orderUid: String = UUID.randomUUID().toString(),
)
