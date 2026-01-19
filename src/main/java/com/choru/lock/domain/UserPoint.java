package com.choru.lock.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long point;

    @Version
    private Long version;

    public UserPoint(Long userId, Long point) {
        this.userId = userId;
        this.point = point;
    }

    public void use(Long amount){
        if(this.point < amount){
            throw new IllegalArgumentException("포인트 잔약이 부족합니다.");
        }

        this.point -= amount;
    }
}
