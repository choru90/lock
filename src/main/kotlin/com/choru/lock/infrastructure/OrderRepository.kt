package com.choru.lock.infrastructure

import com.choru.lock.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long>
