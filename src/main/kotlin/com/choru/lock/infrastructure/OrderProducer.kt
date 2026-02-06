package com.choru.lock.infrastructure

import com.choru.lock.dto.OrderCreateRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class OrderProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {

    fun sendCreateOrder(request: OrderCreateRequest) {
        // 1. DTO를 JSON String으로 직렬화
        val jsonPayload = objectMapper.writeValueAsString(request)

        // 2. Kafka로 전송 (Topic: "order-create")
        // key를 userId로 주면, 동일 유저의 요청은 순서가 보장됨 (Partitioning)
        kafkaTemplate.send("order-create", request.userId.toString(), jsonPayload)
            .whenComplete { result, ex ->
                if (ex != null) {
                    // 실제 운영에선 여기서 Dead Letter Queue(DLQ)로 보내거나 로깅 필수
                    println("Kafka 전송 실패: ${ex.message}")
                } else {
                    println("Kafka 전송 성공: $jsonPayload (Offset: ${result.recordMetadata.offset()})")
                }
            }
    }
}