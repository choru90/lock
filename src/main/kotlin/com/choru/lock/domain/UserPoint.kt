package com.choru.lock.domain

import jakarta.persistence.*

@Entity
class UserPoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val userId: Long,

    var point: Long,

    @Version
    val version: Long? = null
) {
    constructor(userId: Long, point: Long) : this(
        id = null,
        userId = userId,
        point = point,
        version = null
    )

    fun use(amount: Long) {
        if (this.point < amount) {
            throw IllegalArgumentException("포인트 잔약이 부족합니다.")
        }
        this.point -= amount
    }
}
