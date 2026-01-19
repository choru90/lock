package com.choru.lock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long productId;
    private Long couponId;

    public Order(Long userId, Long productId, Long couponId) {
        this.userId = userId;
        this.productId = productId;
        this.couponId = couponId;
    }
}
