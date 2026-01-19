# 🔐 Lock Strategy Study

JPA/Hibernate의 **비관적 락(Pessimistic Lock)** 과 **낙관적 락(Optimistic Lock)** 을 비교 학습하기 위한 프로젝트입니다.

## 📌 프로젝트 개요

동시에 100명의 사용자가 주문을 요청할 때, 다양한 락 전략이 **동시성 문제**를 어떻게 해결하는지 테스트합니다.

### 시나리오
- 쿠폰 100장 (선착순 할인)
- 재고 100개
- 사용자별 포인트 10,000P
- **100명이 동시에 1개씩 주문 → 결과: 쿠폰 0장, 재고 0개, 주문 100건**

---

## 🛠 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.x |
| ORM | Spring Data JPA + Hibernate 7 |
| Database | PostgreSQL |
| Build | Gradle (Kotlin DSL) |

---

## 🏗 프로젝트 구조

```
src/main/java/com/choru/lock/
├── domain/
│   ├── Stock.java          # 재고 엔티티 (@Version 포함)
│   ├── Coupon.java         # 쿠폰 엔티티 (@Version 포함)
│   ├── UserPoint.java      # 사용자 포인트 엔티티 (@Version 포함)
│   ├── Order.java          # 주문 엔티티
│   ├── StockService.java   # 재고 감소 서비스
│   ├── StockFacade.java    # 낙관적 락 재시도 로직
│   └── OrderService.java   # 3가지 락 전략 구현
├── infrastructure/
│   ├── StockRepository.java      # 비관적/낙관적 락 쿼리
│   ├── CouponRepository.java
│   ├── UserPointRepository.java
│   └── OrderRepository.java
└── OrderFacade.java        # 주문 재시도 로직 (낙관적 락용)
```

---

## 🔒 락 전략 비교

### 전략 1: 낙관적 락 (Optimistic Lock)

```java
@Version
private Long version;  // 엔티티에 버전 필드 추가

@Lock(LockModeType.OPTIMISTIC)
@Query("select s from Stock s where s.id = :id")
Optional<Stock> findByIdWithOptimisticLock(Long id);
```

- **동작**: 커밋 시점에 버전 비교 → 충돌 시 `ObjectOptimisticLockingFailureException` 발생
- **장점**: DB 락을 걸지 않아 처리량 높음
- **단점**: 충돌 시 재시도 필요 (Facade 패턴으로 해결)

### 전략 2: 비관적 락 (Pessimistic Lock)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Stock s where s.id = :id")
Optional<Stock> findByIdWithPessimisticLock(Long id);
```

- **동작**: 조회 시점에 `SELECT ... FOR UPDATE` 실행 → 다른 트랜잭션 대기
- **장점**: 재시도 없이 순차 처리
- **단점**: 락 대기 시간 발생, 외부 API 호출 시 락 점유 시간 증가

### 전략 3: 하이브리드 (Best Practice) ⭐

```java
// 공유 자원 (쿠폰, 재고) → 비관적 락
Coupon coupon = couponRepository.findByWithPessimisticLock(couponId);
Stock stock = stockRepository.findByIdWithPessimisticLock(productId);

// 개인 자원 (포인트) → 낙관적 락
UserPoint point = userPointRepository.findByUserId(userId);
```

- **동작**: 경쟁이 심한 자원만 비관적 락, 나머지는 낙관적 락
- **장점**: 락 점유 시간 최소화 + 동시성 보장

---

## 🧪 테스트 실행

### 사전 요구사항
- PostgreSQL 실행 중 (localhost:5432)
- 데이터베이스 생성

### 설정 (`application.yml`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/{DB_NAME}
    username: {USERNAME}
    password: {PASSWORD}
```

### 테스트 실행
```bash
./gradlew clean test
```

### 테스트 케이스

| 테스트 | 설명 |
|--------|------|
| `strategy1_optimistic` | 전략 1 - 낙관적 락 (재시도 발생) |
| `strategy2_pessimistic` | 전략 2 - 비관적 락 (순차 처리) |
| `strategy3_hybrid` | 전략 3 - 하이브리드 (권장) |
| `prove_blocking` | 비관적 락 vs 하이브리드 블로킹 비교 |

---

## 📊 성능 비교 예시

| 전략 | 소요 시간 | 재시도 횟수 | 특징 |
|------|-----------|-------------|------|
| 낙관적 락 | ~3000ms | 많음 | 충돌 빈번, 재시도 오버헤드 |
| 비관적 락 | ~2000ms | 0 | 락 대기, 순차 처리 |
| 하이브리드 | ~1500ms | 0 | 균형잡힌 성능 |

---

## 🧠 핵심 개념

### @Version 어노테이션
```java
@Version
private Long version;
```
- 낙관적 락의 핵심
- UPDATE 시 `WHERE version = ?` 조건 자동 추가
- 버전 불일치 → 예외 발생

### Facade 패턴 (재시도 로직)
```java
while (true) {
    try {
        service.decrease(id, quantity);
        break;
    } catch (ObjectOptimisticLockingFailureException e) {
        Thread.sleep(50);  // 재시도
    }
}
```

---

## 🎯 학습 포인트

1. **락 선택 기준**: 충돌 빈도에 따라 전략 선택
2. **하이브리드 전략**: 실무에서 가장 효과적인 접근법
3. **외부 API 영향**: 락 점유 중 외부 호출 시 전체 처리량 저하
4. **테스트 중요성**: 동시성 문제는 단위 테스트로 발견 어려움

---

## 📚 참고 자료

- [JPA Locking - Baeldung](https://www.baeldung.com/jpa-pessimistic-locking)
- [Optimistic vs Pessimistic Locking](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)
