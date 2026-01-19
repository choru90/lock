package com.choru.lock.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int stock;

    @Version
    private Long version;

    public Coupon(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

    public void decrease(){
        if(this.stock <= 0 ){
            throw new IllegalStateException("쿠폰이 모두 소진 되었습니다.");
        }
        this.stock--;
    }
}
