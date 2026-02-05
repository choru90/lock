package com.choru.lock.infrastructure

import com.choru.lock.domain.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository: JpaRepository<Product, Long> {
}