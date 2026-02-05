package com.choru.lock.domain

import jakarta.persistence.*

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val userId: Long,

    val productId: Long,

    val couponId: Long
) {
    constructor(userId: Long, productId: Long, couponId: Long) : this(
        id = null,
        userId = userId,
        productId = productId,
        couponId = couponId
    )
}
