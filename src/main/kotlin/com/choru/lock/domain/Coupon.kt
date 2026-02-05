package com.choru.lock.domain

import jakarta.persistence.*

@Entity
class Coupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val name: String,

    var stock: Int,

    @Version
    val version: Long? = null
) {
    constructor(name: String, stock: Int) : this(
        id = null,
        name = name,
        stock = stock,
        version = null
    )

    fun decrease() {
        if (this.stock <= 0) {
            throw IllegalStateException("쿠폰이 모두 소진 되었습니다.")
        }
        this.stock--
    }
}
