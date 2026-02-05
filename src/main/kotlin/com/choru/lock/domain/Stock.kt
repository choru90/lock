package com.choru.lock.domain

import jakarta.persistence.*

@Entity
class Stock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val productId: Long,

    var quantity: Long,

    @Version
    val version: Long? = null
) {
    constructor(productId: Long, quantity: Long) : this(
        id = null,
        productId = productId,
        quantity = quantity,
        version = null
    )

    fun decrease(quantity: Long) {
        if (this.quantity - quantity < 0) {
            throw RuntimeException("재고는 0개 미만이 될 수 없습니다.")
        }
        this.quantity -= quantity
    }
}
